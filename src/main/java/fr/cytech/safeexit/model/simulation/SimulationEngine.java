package fr.cytech.safeexit.model.simulation;

import fr.cytech.safeexit.model.agent.Agent;
import fr.cytech.safeexit.model.agent.AgentState;
import fr.cytech.safeexit.model.graph.Edge;
import fr.cytech.safeexit.model.graph.Graph;
import fr.cytech.safeexit.model.graph.Node;
import fr.cytech.safeexit.model.graph.NodeType;
import fr.cytech.safeexit.model.observer.AbstractObservable;
import fr.cytech.safeexit.model.observer.Observer;
import fr.cytech.safeexit.model.observer.SimulationEvent;
import fr.cytech.safeexit.model.routing.VoronoiEvacuationRouter;
import fr.cytech.safeexit.model.sector.DisplayPanel;
import fr.cytech.safeexit.model.sector.PanelMessage;
import fr.cytech.safeexit.model.sector.PanelMode;
import fr.cytech.safeexit.model.sector.Sector;
import fr.cytech.safeexit.model.sector.SectorManager;
import fr.cytech.safeexit.model.sensor.SeatSensor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;

/**
 * Drives the simulation one cycle at a time.
 * <p>
 * Each call to {@link #tick()} advances every active agent by one step,
 * applying the "check before entering" capacity policy: an agent only steps
 * onto an edge if it still has room, otherwise it freezes for a couple of
 * cycles. Movement inside a node is instantaneous; bottlenecks happen on the
 * edges. The engine notifies observers of the events it produces and recomputes
 * the Voronoi routes when an obstacle appears.
 *
 * @author GROUPE E
 * @version 1.0
 */
public class SimulationEngine extends AbstractObservable implements Observer {

    /** Scales pixel distances into per-cycle progress. */
    private static final double SPEED_SCALE = 25.0;

    private final SimulationState state;
    private final Graph graph;
    private final VoronoiEvacuationRouter router;
    private final SimulationClock clock;

    private boolean paused;
    private boolean routesDirty;
    private EventPhase phase = EventPhase.NORMAL;
    private final Random random = new Random();

    /** Number of cycles between two attempts to send a spectator wandering. */
    private static final int AMBIENT_TRIP_INTERVAL = 6;
    /** Visual progress added per cycle while a wandering spectator crosses an edge. */
    private static final double AMBIENT_STEP = 0.34;
    /** Cycles a spectator lingers outside (via an exit) before walking back to their seat. */
    private static final int OUTSIDE_WAIT = 20;
    /** Percentage of ambient trips that step outside through an exit instead of to an amenity. */
    private static final int OUTSIDE_TRIP_PERCENT = 30;
    /** Spectators currently away from their seat, with the state of their round trip. */
    private final Map<Agent, AmbientTrip> ambientTrips = new HashMap<>();
    /** Lazily-built list of "amenity" nodes (concourse / restrooms) agents walk to. */
    private List<Node> amenityCache;
    /** Lazily-built list of exit nodes spectators may briefly step outside through. */
    private List<Node> exitCache;

    /**
     * Creates an engine for the given simulation state and computes the initial
     * routes.
     *
     * @param state the simulation state (graph and agents); never {@code null}
     */
    public SimulationEngine(SimulationState state) {
        if (state == null) {
            throw new IllegalArgumentException("Engine requires a simulation state");
        }
        this.state = state;
        this.graph = state.getGraph();
        this.router = new VoronoiEvacuationRouter(graph, state);
        this.clock = new SimulationClock();
        attachToModel();
        // The movement tracker records every AGENT_MOVED / AGENT_STATE_CHANGED event.
        addObserver(state.getAgentTracker());
        recomputeRoutes();
    }

    /**
     * Subscribes the engine to the graph and to every node and edge so it is
     * notified when something is blocked or unblocked.
     */
    private void attachToModel() {
        graph.addObserver(this);
        for (Node node : graph.getNodes()) {
            node.addObserver(this);
        }
        for (Edge edge : graph.getEdges()) {
            edge.addObserver(this);
        }
    }

    /**
     * Recomputes the Voronoi routes and refreshes each agent's assigned exit.
     */
    public void recomputeRoutes() {
        router.computeRoutes();
        for (Agent agent : state.getAgents()) {
            if (agent.getCurrentNode() != null) {
                agent.setTargetExit(state.getAssignedExit(agent.getCurrentNode()));
            }
        }
        routesDirty = false;
        notifyObservers(new SimulationEvent(SimulationEvent.Type.ROUTE_RECALCULATED, this));
    }

    /**
     * Advances the simulation by one cycle.
     */
    public void tick() {
        if (paused) {
            return;
        }
        if (routesDirty) {
            recomputeRoutes();
        }
        if (phase == EventPhase.NORMAL) {
            // Event in progress: only monitor the venue (ambient occupancy changes).
            ambientTick();
        } else {
            // Emergency: move every agent toward the exits.
            for (Agent agent : state.getAgents()) {
                stepAgent(agent);
            }
        }
        checkDensityAlerts();
        state.incrementCycle();
    }

    /**
     * Simulates the living venue during the {@link EventPhase#NORMAL} phase:
     * spectators occasionally leave their seat to walk to the concourse /
     * restrooms and come back. Each such agent physically travels the graph, so
     * the view shows a moving dot and the seat frees up (AWAY) then fills again
     * (OCCUPIED) when the spectator returns.
     */
    private void ambientTick() {
        // Advance every spectator currently away for a walk.
        for (Agent agent : new ArrayList<>(ambientTrips.keySet())) {
            AmbientTrip trip = ambientTrips.get(agent);
            if (trip != null) {
                ambientStep(agent, trip);
            }
        }
        // Now and then, send another seated spectator on a round trip.
        maybeStartAmbientTrip();
    }

    /**
     * Advances one wandering agent by a single cycle. Ambient movement is purely
     * visual: it updates the agent's own position fields (so the canvas animates
     * a moving dot) and emits {@code AGENT_MOVED}, but it does <b>not</b> alter
     * node or edge occupancy except for the agent's own home seat. A spectator
     * may therefore shuffle past seated neighbours without disturbing them.
     *
     * @param agent the wandering agent
     * @param trip  its ongoing round trip (path ends back at its home seat)
     */
    private void ambientStep(Agent agent, AmbientTrip trip) {
        Deque<Node> route = trip.route;
        // Currently crossing an edge: advance the dot visually.
        if (agent.getCurrentEdge() != null) {
            double progress = agent.getProgressOnEdge() + AMBIENT_STEP;
            if (progress < 1.0) {
                agent.setProgressOnEdge(progress);
                notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
                return;
            }
            Node arrived = agent.getEdgeDestination();
            agent.setCurrentEdge(null);
            agent.setEdgeDestination(null);
            agent.setProgressOnEdge(0.0);
            agent.setCurrentNode(arrived);
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
            // Reached the exit of an "outside" trip: step out of the hall and linger.
            if (trip.exitNode != null && arrived != null && arrived.equals(trip.exitNode)) {
                trip.outside = true;
                trip.outsideWait = OUTSIDE_WAIT;
            }
            if (route.isEmpty() && arrived != null && arrived.getType() == NodeType.SEAT) {
                finishAmbientTrip(agent);
            }
            return;
        }
        // Lingering outside the hall: wait a few cycles before walking back in.
        if (trip.outsideWait > 0) {
            trip.outsideWait--;
            return;
        }
        // Standing on a node: start crossing the next edge of the route.
        Node current = agent.getCurrentNode();
        if (current == null) {
            ambientTrips.remove(agent);
            return;
        }
        if (route.isEmpty()) {
            finishAmbientTrip(agent);
            return;
        }
        Node next = route.pollFirst();
        if (next.equals(current)) {
            return;
        }
        Edge edge = graph.getEdgeBetween(current, next);
        if (edge == null) {
            finishAmbientTrip(agent);
            return;
        }
        boolean leavingHome = current.equals(trip.home);
        agent.setCurrentNode(null);
        agent.setCurrentEdge(edge);
        agent.setEdgeDestination(next);
        agent.setProgressOnEdge(0.0);
        if (leavingHome) {
            current.decrementAgentCount();
            if (state.getSensorNetwork() != null) {
                SeatSensor sensor = state.getSensorNetwork().getSensor(current);
                if (sensor != null) {
                    sensor.markAway(); // seat reserved while the spectator is out
                }
            }
        }
        notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
    }

    /**
     * Ends a round trip: the spectator sits back down on its home seat, which
     * becomes OCCUPIED again.
     *
     * @param agent the returning agent
     */
    private void finishAmbientTrip(Agent agent) {
        AmbientTrip trip = ambientTrips.remove(agent);
        Node home = (trip != null) ? trip.home : null;
        Node seat = (home != null) ? home : agent.getCurrentNode();
        if (seat != null && seat.getType() == NodeType.SEAT) {
            agent.setCurrentNode(seat);
            agent.setCurrentEdge(null);
            agent.setEdgeDestination(null);
            agent.setProgressOnEdge(0.0);
            seat.incrementAgentCount();
            if (state.getSensorNetwork() != null) {
                SeatSensor sensor = state.getSensorNetwork().getSensor(seat);
                if (sensor != null) {
                    sensor.markOccupied(agent);
                }
            }
        }
    }

    /**
     * Occasionally picks a seated, calm spectator and sends it on a round trip
     * to a random amenity node and back, within a small concurrency cap.
     */
    private void maybeStartAmbientTrip() {
        if (state.getCurrentCycle() % AMBIENT_TRIP_INTERVAL != 0) {
            return;
        }
        int cap = Math.max(1, state.getAgents().size() / 25);
        if (ambientTrips.size() >= cap) {
            return;
        }
        // Now and then the spectator steps outside through an exit (toilets / bar
        // outside the hall); otherwise they just walk to an amenity and back.
        boolean stepOutside = !exitNodes().isEmpty()
                && random.nextInt(100) < OUTSIDE_TRIP_PERCENT;
        List<Node> destinations = stepOutside ? exitNodes() : amenityNodes();
        if (destinations.isEmpty()) {
            return;
        }
        List<Agent> candidates = new ArrayList<>();
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited() || ambientTrips.containsKey(agent)
                    || agent.getState() != AgentState.CALM) {
                continue;
            }
            Node node = agent.getCurrentNode();
            if (node != null && node.getType() == NodeType.SEAT) {
                candidates.add(agent);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        Agent agent = candidates.get(random.nextInt(candidates.size()));
        Node seat = agent.getCurrentNode();
        Node target = destinations.get(random.nextInt(destinations.size()));
        List<Node> forward = shortestPath(seat, target);
        List<Node> backward = shortestPath(target, seat);
        if (forward.isEmpty() || backward.isEmpty()) {
            return;
        }
        Deque<Node> route = new ArrayDeque<>();
        route.addAll(forward);
        route.addAll(backward);
        ambientTrips.put(agent, new AmbientTrip(seat, route, stepOutside ? target : null));
    }

    /**
     * Returns the nodes a wandering spectator can walk to (corridors and
     * intersections represent the concourse and restrooms). Cached after first use.
     *
     * @return the list of amenity nodes (possibly empty)
     */
    private List<Node> amenityNodes() {
        if (amenityCache == null) {
            amenityCache = new ArrayList<>();
            for (Node node : graph.getNodes()) {
                if ((node.getType() == NodeType.CORRIDOR
                        || node.getType() == NodeType.CROSS_SECTION)
                        && !node.isBlocked()) {
                    amenityCache.add(node);
                }
            }
        }
        return amenityCache;
    }

    /**
     * Returns the exit nodes a spectator may briefly step outside through during
     * the NORMAL phase (toilets / bar outside the hall). Cached after first use.
     * In NORMAL mode these exits are not evacuation targets, only temporary
     * stepping-out points.
     *
     * @return the list of exit nodes (possibly empty)
     */
    private List<Node> exitNodes() {
        if (exitCache == null) {
            exitCache = new ArrayList<>();
            for (Node node : graph.getNodes()) {
                if (node.getType() == NodeType.EXIT && !node.isBlocked()) {
                    exitCache.add(node);
                }
            }
        }
        return exitCache;
    }

    /**
     * Breadth-first shortest path (in hops) between two nodes, avoiding blocked
     * nodes. The returned list excludes the start and ends at the target.
     *
     * @param start  the start node
     * @param target the target node
     * @return the path as a list of nodes, or an empty list if unreachable
     */
    private List<Node> shortestPath(Node start, Node target) {
        Map<Node, Node> previous = new HashMap<>();
        Set<Node> visited = new HashSet<>();
        Deque<Node> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            if (node.equals(target)) {
                break;
            }
            for (Node neighbour : graph.getNeighbors(node)) {
                if (visited.contains(neighbour) || neighbour.isBlocked()) {
                    continue;
                }
                visited.add(neighbour);
                previous.put(neighbour, node);
                queue.add(neighbour);
            }
        }
        if (!visited.contains(target)) {
            return new ArrayList<>();
        }
        Deque<Node> path = new ArrayDeque<>();
        for (Node node = target; node != null && !node.equals(start); node = previous.get(node)) {
            path.addFirst(node);
        }
        return new ArrayList<>(path);
    }

    /**
     * Triggers the emergency evacuation: switches to the evacuation phase and
     * notifies observers. Seated spectators (even those marked away) now head
     * for the exits.
     */
    public void triggerEvacuation() {
        if (phase == EventPhase.EVACUATION) {
            return;
        }
        phase = EventPhase.EVACUATION;
        // Resolve every wandering spectator so the evacuation starts from a clean
        // state: those still inside rush back to their seat; those who already
        // stepped outside are safe and simply leave the simulation.
        for (Map.Entry<Agent, AmbientTrip> entry
                : new ArrayList<>(ambientTrips.entrySet())) {
            Agent agent = entry.getKey();
            AmbientTrip trip = entry.getValue();
            if (trip.outside) {
                // Already outside the hall: drop them (their seat stays free).
                state.getAgents().remove(agent);
                continue;
            }
            Node home = trip.home;
            agent.setCurrentEdge(null);
            agent.setEdgeDestination(null);
            agent.setProgressOnEdge(0.0);
            if (home != null) {
                agent.setCurrentNode(home);
                home.incrementAgentCount();
                if (state.getSensorNetwork() != null) {
                    SeatSensor sensor = state.getSensorNetwork().getSensor(home);
                    if (sensor != null) {
                        sensor.markOccupied(agent);
                    }
                }
            }
        }
        ambientTrips.clear();
        // Switch every sector panel to evacuation guidance and stop congestion
        // monitoring, so the panels keep pointing to the exits instead of
        // reverting to a standby "welcome" message as the seats empty.
        broadcastEvacuationToPanels();
        notifyObservers(new SimulationEvent(SimulationEvent.Type.EVACUATION_TRIGGERED, this));
    }

    /**
     * Puts every sector display panel into evacuation guidance: each panel shows
     * the exit assigned to its sector (Voronoi cell) with an arrow pointing that
     * way, and congestion monitoring is disabled so the message is not
     * overwritten as the venue empties.
     */
    private void broadcastEvacuationToPanels() {
        SectorManager manager = state.getSectorManager();
        if (manager == null) {
            return;
        }
        manager.setMonitoring(false);
        for (Sector sector : manager.getSectors()) {
            DisplayPanel panel = sector.getPanel();
            if (panel == null) {
                continue;
            }
            Node centre = sector.getCentreNode();
            Node exit = (centre != null) ? state.getAssignedExit(centre) : null;
            if (exit != null) {
                panel.broadcast(PanelMessage.evacuation(
                        exit.getId().replace("EXIT_", ""), directionTo(centre, exit)));
            } else {
                panel.broadcast(PanelMessage.custom(
                        "ÉVACUATION EN COURS — SUIVEZ LES ISSUES",
                        PanelMessage.ArrowDirection.NONE, PanelMode.DIRECTIONAL_GUIDANCE));
            }
        }
    }

    /**
     * Returns a rough cardinal arrow direction from one node toward another,
     * based on their positions (screen coordinates, so y grows downwards).
     *
     * @param from the origin node
     * @param to   the target node
     * @return the dominant direction toward {@code to}
     */
    private static PanelMessage.ArrowDirection directionTo(Node from, Node to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        if (Math.abs(dx) >= Math.abs(dy)) {
            return dx >= 0 ? PanelMessage.ArrowDirection.EAST : PanelMessage.ArrowDirection.WEST;
        }
        return dy >= 0 ? PanelMessage.ArrowDirection.SOUTH : PanelMessage.ArrowDirection.NORTH;
    }

    /**
     * Returns the current event phase.
     *
     * @return the phase
     */
    public EventPhase getPhase() {
        return phase;
    }

    /**
     * Indicates whether an evacuation is in progress.
     *
     * @return {@code true} if the phase is {@link EventPhase#EVACUATION}
     */
    public boolean isEvacuating() {
        return phase == EventPhase.EVACUATION;
    }

    /**
     * Advances a single agent by one cycle.
     *
     * @param agent the agent to move
     */
    private void stepAgent(Agent agent) {
        if (agent.hasExited()) {
            return;
        }
        // Agent currently travelling along an edge: advance its progress.
        if (agent.getCurrentEdge() != null) {
            advanceOnEdge(agent);
            return;
        }
        // Frozen agent: wait, then retry by becoming calm again.
        if (agent.getState() == AgentState.FROZEN) {
            if (agent.canMove()) {
                agent.setState(AgentState.CALM);
            } else {
                agent.decrementDelay();
            }
            return;
        }
        // Agent standing on a node: decide where to go and try to enter an edge.
        Node current = agent.getCurrentNode();
        Node next = agent.chooseNextNode(graph, state);
        if (next == null || next.equals(current)) {
            return;
        }
        Edge edge = graph.getEdgeBetween(current, next);
        if (edge == null) {
            return;
        }
        if (edge.hasRoom() && !next.isBlocked()) {
            enterEdge(agent, current, edge, next);
        } else {
            // No room: wait a couple of cycles (heavy congestion).
            agent.setState(AgentState.FROZEN);
        }
    }

    /**
     * Makes an agent leave its node and step onto an edge.
     *
     * @param agent   the agent
     * @param from    the node it is leaving
     * @param edge    the edge it enters
     * @param toNode  the node at the other end of the edge
     */
    private void enterEdge(Agent agent, Node from, Edge edge, Node toNode) {
        from.decrementAgentCount();
        // Free the seat sensor in real time when its occupant leaves.
        if (from.getType() == fr.cytech.safeexit.model.graph.NodeType.SEAT
                && state.getSensorNetwork() != null) {
            state.getSensorNetwork().onAgentLeftSeat(from);
        }
        edge.onAgentEnter();
        agent.setCurrentNode(null);
        agent.setCurrentEdge(edge);
        agent.setEdgeDestination(toNode);
        agent.setProgressOnEdge(0.0);
        notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
    }

    /**
     * Advances an agent already travelling on an edge, handling arrival.
     *
     * @param agent the agent
     */
    private void advanceOnEdge(Agent agent) {
        Edge edge = agent.getCurrentEdge();
        double increment = (agent.getCurrentSpeed() * edge.getSpeedModifier() * SPEED_SCALE)
                / Math.max(1.0, edge.getLength());
        double progress = agent.getProgressOnEdge() + increment;

        if (progress < 1.0) {
            agent.setProgressOnEdge(progress);
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
            return;
        }

        // Arrived at the destination node.
        Node destination = agent.getEdgeDestination();
        edge.onAgentLeave();
        agent.setCurrentEdge(null);
        agent.setEdgeDestination(null);
        agent.setProgressOnEdge(0.0);
        agent.setCurrentNode(destination);
        destination.incrementAgentCount();

        if (destination.isExit()) {
            agent.setState(AgentState.EXITED);
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_REACHED_EXIT, agent));
        } else {
            notifyObservers(new SimulationEvent(SimulationEvent.Type.AGENT_MOVED, agent));
        }
    }

    /**
     * Emits a density alert for every node currently in heavy congestion.
     */
    private void checkDensityAlerts() {
        for (Node node : graph.getNodes()) {
            if (node.isCongested()) {
                notifyObservers(new SimulationEvent(SimulationEvent.Type.DENSITY_ALERT, node));
            }
        }
    }

    /**
     * Manually triggers panic in a whole sector: every seated agent of that
     * sector switches to {@link AgentState#PANICKED} (and therefore to the
     * panicked behaviour strategy), and the sector panel shows an alert. This is
     * the supervisor's "create a situation" tool used in demonstrations.
     *
     * @param sectorId the sector identifier (e.g. "SEC_1")
     * @return the number of agents that switched to panic
     */
    public int triggerPanicInSector(String sectorId) {
        SectorManager manager = state.getSectorManager();
        if (manager == null) {
            return 0;
        }
        Sector sector = manager.getSectorById(sectorId);
        if (sector == null) {
            return 0;
        }
        Set<String> rows = new HashSet<>(sector.getRowLabels());
        int affected = 0;
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited()) {
                continue;
            }
            Node node = agent.getCurrentNode();
            if (node != null && node.getType() == NodeType.SEAT
                    && rows.contains(rowLabelOf(node.getId()))) {
                agent.setState(AgentState.PANICKED);
                affected++;
            }
        }
        if (sector.getPanel() != null) {
            sector.getPanel().broadcast(PanelMessage.alert("MOUVEMENT DE PANIQUE \u2014 " + sectorId));
        }
        return affected;
    }

    /**
     * Calms a whole sector down again: every panicked spectator of that sector
     * returns to {@link AgentState#CALM} and its panel goes back to standby. This
     * is the counterpart of {@link #triggerPanicInSector(String)} so a scenario
     * can be undone live during a demonstration.
     *
     * @param sectorId the sector identifier (e.g. "SEC_1")
     * @return the number of agents that returned to calm
     */
    public int calmSector(String sectorId) {
        SectorManager manager = state.getSectorManager();
        if (manager == null) {
            return 0;
        }
        Sector sector = manager.getSectorById(sectorId);
        if (sector == null) {
            return 0;
        }
        Set<String> rows = new HashSet<>(sector.getRowLabels());
        int affected = 0;
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited()) {
                continue;
            }
            Node node = agent.getCurrentNode();
            if (node != null && node.getType() == NodeType.SEAT
                    && rows.contains(rowLabelOf(node.getId()))
                    && agent.getState() == AgentState.PANICKED) {
                agent.setState(AgentState.CALM);
                affected++;
            }
        }
        if (sector.getPanel() != null) {
            sector.getPanel().reset();
        }
        return affected;
    }

    /**
     * Extracts the row label from a seat identifier ("SEAT_A_3" -&gt; "A").
     *
     * @param seatId the seat identifier
     * @return the row label, or the whole id if it does not match the pattern
     */
    private static String rowLabelOf(String seatId) {
        String[] parts = seatId.split("_");
        return parts.length >= 2 ? parts[1] : seatId;
    }

    /**
     * Returns the number of agents that have reached an exit.
     *
     * @return the count of evacuated agents
     */
    public int countEvacuated() {
        int n = 0;
        for (Agent agent : state.getAgents()) {
            if (agent.hasExited()) {
                n++;
            }
        }
        return n;
    }

    /**
     * Returns how many spectators have temporarily stepped outside the hall
     * (through an exit) during the NORMAL phase. Handy for the supervisor view.
     *
     * @return the number of spectators currently outside
     */
    public int countSpectatorsOutside() {
        int n = 0;
        for (AmbientTrip trip : ambientTrips.values()) {
            if (trip.outside) {
                n++;
            }
        }
        return n;
    }

    /**
     * Indicates whether every agent has evacuated.
     *
     * @return {@code true} if all agents have exited
     */
    public boolean isEvacuationComplete() {
        return countEvacuated() == state.getAgents().size();
    }

    /**
     * Reacts to model events: marks the routes as needing a recompute when an
     * obstacle appears or disappears.
     *
     * @param event the event broadcast by the model
     */
    @Override
    public void update(SimulationEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case EDGE_BLOCKED, EDGE_UNBLOCKED, NODE_STATE_CHANGED -> routesDirty = true;
            default -> { /* ignored */ }
        }
    }

    /**
     * Returns the simulation state driven by this engine.
     *
     * @return the simulation state
     */
    public SimulationState getState() {
        return state;
    }

    /**
     * Returns the simulation clock.
     *
     * @return the clock
     */
    public SimulationClock getClock() {
        return clock;
    }

    /**
     * Indicates whether the simulation is paused.
     *
     * @return {@code true} if paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Pauses or resumes the simulation.
     *
     * @param paused the new paused state
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * State of a spectator's ongoing round trip away from their seat during the
     * {@link EventPhase#NORMAL} phase. Groups what used to be two parallel maps so
     * a trip can also model a spectator briefly stepping outside through an exit.
     */
    private static final class AmbientTrip {
        /** Home seat to free on departure and re-occupy on return. */
        private final Node home;
        /** Remaining nodes to walk; the path ends back at {@link #home}. */
        private final Deque<Node> route;
        /** Exit reached before lingering outside, or {@code null} for an inside trip. */
        private final Node exitNode;
        /** Cycles left lingering outside before walking back in (0 = not lingering). */
        private int outsideWait;
        /** {@code true} once the spectator has stepped outside through {@link #exitNode}. */
        private boolean outside;

        AmbientTrip(Node home, Deque<Node> route, Node exitNode) {
            this.home = home;
            this.route = route;
            this.exitNode = exitNode;
        }
    }
}