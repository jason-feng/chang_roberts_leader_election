import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


/**
 * Network class that holds multiple nodes, each running on its own thread.
 * Synchronous communication: each round lasts for 20ms
 * At each round the network receives the messages that the nodes want to send and delivers them
 * The network should make sure that:
	- A node can only send messages to its neighbours
	- A node can only send one message per neighbour per round
	- When a node fails, the network must inform all the node's neighbours about the failure
 * @author Jason Feng
 * Distributed Systems Fall 2015
 */
public class Network {

	private static List<Node> nodes;			// The list of nodes in our network
	private int round;							// The current round we are on
	private int period = 20;					// Length of each communication round
	private Map<Integer, String> msgsToDeliver; // Integer for the id of the sender and String for the message
	private Map<Integer, String> roundMsgs; 	// Integer is the round, String is the message
	private LinkedList<Integer> failureMsgs;	// Keeps track of failures
	private boolean init_neighbors = false;		// Whether or not we have initialized the neighbors for each node
	private BufferedWriter bw;					// BufferedWriter to write to output file
	
	
	/**
	 * Initializes the network. 
	 */
	public Network() {
		nodes = new LinkedList<Node>();
		msgsToDeliver = new ConcurrentHashMap<Integer, String>();
		roundMsgs = new HashMap<Integer, String>();
		failureMsgs = new LinkedList<Integer>();
		
		File file = new File("output.txt");
		if (!file.exists()) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		try {
			bw = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * @return Total number of nodes
	 */
	public int numNodes() {
		return nodes.size();
	}
	
	/**
	 * @return BufferedWriter
	 */
	public BufferedWriter getBw() {
		return bw;
	}
	
	/**
	 * Helper method to check if we have created a node with the given id
	 * @param id
	 * @return True if contains, else false
	 */
	public boolean contains(int id) {
		boolean result = false;
		for (Node node : nodes) {
			if (node.getNodeId() == id) {
				result = true;
				break;
			}
		}
		return result;
	}

	/**
	 * Helper method to get a node from our list
	 * @param id
	 * @return Node if contains, else return null
	 */
	public Node getNodeById(int id) {
		if (!contains(id)) {
			return null;
		}
		for (Node node : nodes) {
			if (node.getNodeId() == id) {
				return node;
			}
		}
		return null;
	}

	/**
	 * @param index
	 * @return The node in the given index of our ring
	 */
	public Node getNodeByIndex(int index) {
		return nodes.get(index);
	}
	
	/**
	 * Given a node, returns the index of the node in the ring
	 * @param node
	 * @return index
	 */
	public int getIndexOfNode(Node node) {
		return nodes.lastIndexOf(node);
	}
	
	/**
	 * @return the current round
	 */
	public int getRound() {
		return round;
	}
	
	/**
	 * Parses the Input File to setup the Network Graph
	 * @param fileName
	 * @throws IOException
	 */
	private void parseFile(String fileName) throws IOException {
		System.out.println("Parsing input file");
		Path path = Paths.get(fileName);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			String line = null;
			line = br.readLine(); // Skip first line
			while ((line = br.readLine()) != null) {
				parseLine(line);
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}
	}	

	/**
	 * There are three types of lines we are parsing:
	 * 	Neighbor lines: Formatted with id and a list of neighbors
	 * 	Elect lines: Formatted with ELECT, round number, and a list of nodes that start leader election
	 * 	Fail lines: Formatted with FAIL, id of node that failed
	 * @param line
	 */
	private void parseLine(String line) {
		if (line != null && line.length() > 0) {
			String[] split_line = line.split(" ");
			String instruction_type = split_line[0];
			if (instruction_type.equals("ELECT")) {    		
				if (init_neighbors == false) {
					init_neighbors = true;
					addNeighbors();					
				}
				int round = Integer.parseInt(split_line[1]);
				roundMsgs.put(round, line);
			}
			else if (instruction_type.equals("FAIL")) {
				failureMsgs.add(Integer.parseInt(split_line[1]));
			}
			else { // Adding neighbors
				int id = Integer.parseInt(split_line[0]);

				// Checks if our nodes contains this new node we are trying to add
				if (!contains(id)) {
					nodes.add(new Node(id, this));
				}
				split_line[0] = "NEIGHBORS";
				String joined_string = String.join(" ", split_line);
				addMessage(id, joined_string);
			}
		}
	}

	/**
	 * Gets the message for a certain round
	 * @param round
	 */
	private void parseRoundMsg(int round) {
		String msg = roundMsgs.get(round);
		String[] split_line = msg.split(" ");
		String instruction_type = split_line[0];
		if (instruction_type.equals("ELECT")) {
			for (int i = 2; i < split_line.length; i++) {
				int id = Integer.parseInt(split_line[i]);
				addMessage(id, "ELECT START");
			}			
		}
		roundMsgs.remove(round);
	}
	
	/**
	 * Collects all of the messages that nodes want to send
	 * @param node id
	 * @param message m
	 */
	public synchronized void addMessage(int id, String m) {
		System.out.println(this + " receives: " + m + " for Node: " + id);
		if (msgsToDeliver.containsKey(id)) {
			Node node = getNodeById(id);
			deliverMsg(node);
		}
		msgsToDeliver.put(id, m);
	}

	/**
	 * Network delivers all messages that it has collected from the nodes
	 * A node can send only to its neighbours, one message per round per neighbour.
	 * Once a message is sent, it is deleted from our map
	 */
	public synchronized void deliverMsgs() {
		while(!roundMsgs.isEmpty() || !msgsToDeliver.isEmpty() || !failureMsgs.isEmpty()) {
			long start_time = System.currentTimeMillis();
			// Count round time
			while (System.currentTimeMillis() <= (start_time + period)) {
				// Check if we have a round message
				if (roundMsgs.containsKey(round)) {
					parseRoundMsg(round);
				}
				// Deliver the current messages in the map
				Iterator<Integer> ids = msgsToDeliver.keySet().iterator();
				while (ids.hasNext()) {
					int id = ids.next();
					Node node = getNodeById(id);
					deliverMsg(node);
					node.processMsg();
				}
				// If we are done with all of the elections, start the failures
				if (roundMsgs.isEmpty() && msgsToDeliver.isEmpty() && !failureMsgs.isEmpty()) {
					int id = failureMsgs.removeFirst();
					Node node = getNodeById(id);
					addMessage(id, "FAIL");
					deliverMsg(node);
					node.processMsg();
				}
				round++;	
			}
		}
		try {
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Delivers a single message to a node
	 * @param node
	 */
	public synchronized void deliverMsg(Node node) {
		if (node != null) {
			int id = node.getNodeId();
			System.out.println(this + " sends msg to " + node + " " + msgsToDeliver.get(id));
			String msg = msgsToDeliver.get(id);
			node.receiveMsg(msg);
			msgsToDeliver.remove(id);
		}	
	}
	
	/**
	 * Initializes the neighbors for each node
	 */
	public void addNeighbors() {
		System.out.println(this + "Initalizing Neighbors");
		for (int i = 0; i < nodes.size(); i++) {
			Node node = nodes.get(i);
			deliverMsg(node);
		}
	}

	/**
	 * Informs neighbors that node with id failed
	 * @param id
	 */
	public synchronized void informNodeFailure(int id) {
		Node failed_node = getNodeById(id);
		System.out.println("Round: " + getRound() + failed_node + " failed");
		for (Node node : failed_node.myNeighbours) {
			node.myNeighbours.remove(failed_node);
		}
		nodes.remove(failed_node);
	}

	/**
	 * Testing method to print out the neighbors of each node
	 */
	public void printNeighbors() {
		for (int i = 0; i < nodes.size(); i++) {
			System.out.println(nodes.get(i) + " " + nodes.get(i).myNeighbours);
		}	
	}
	
	/**
	 * Testing method to print out the current leader
	 */
	public String printLeader() {
		for (Node node : nodes) {
			if (node.isNodeLeader()) {
				return("LEADER " + node.getNodeId() + "\n");
			}
		}
		return null;
	}

	/**
	 * @return String representation of Network
	 */
	public String toString() {
		return "Round: " + getRound() + " Network";
	}
	
	public static void main(String args[]) throws IOException, InterruptedException {
		if (args.length == 0) {
			System.out.println("Error - No Input File Provided");
			System.exit(1);
		}
		System.out.println("Starting up the Network...");
		Network network = new Network();
		network.parseFile(args[0]);
		network.deliverMsgs();
	}	
}
