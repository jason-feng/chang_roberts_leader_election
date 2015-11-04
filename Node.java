import java.util.*;

import javax.swing.SwingUtilities;

import java.io.*;
import java.net.Socket;

/**
 * Node class that runs on its own thread. 
 * @author Jason Feng
 * Distributed Systems Fall 2015
 */
public class Node extends Thread {
	
	private PrintWriter out;					// Writes output to Network
	private BufferedReader in;					// Reads input from Network
	
	private int id;								// Id of the node
	private boolean participant = false;		// Participant status
	private boolean leader = false;				// Leader status
	private Network network;					// Network that the node belongs to
	private Node node_leader;
	public List<Node> myNeighbours; 			// List of Neighbouring nodes
	public LinkedList<String> incomingMsg; 		// Queues for the incoming messages
	
	public Node(int id, Network network){	
		this.id = id;
		this.network = network;        
		this.node_leader = null;
		
		myNeighbours = new ArrayList<Node>();
		incomingMsg = new LinkedList<String>();
		this.start();
	}
		
	/**
	 * 
	 * @return ID of node
	 */
	public int getNodeId() {
		return id;
	}
			
	/**
	 * 
	 * @return True if node is leader, otherwise False
	 */
	public boolean isNodeLeader() {
		return leader;
	}
		
	/**
	 * 
	 * @return List of the neighbors of the node
	 */
	public List<Node> getNeighbors() {
		return myNeighbours;
	}
		
	/**
	 * Adds node to the list of neighbors
	 * @param Node n
	 */
	public void addNeighbour(Node n) {
		myNeighbours.add(n);
	}


	/**
	 * Receives a message from another node through the network
	 * and adds it to the queue of messages
	 * @param m
	 */
	public void receiveMsg(String m) {
		incomingMsg.add(m);
	}

	/**
	 * Parses a message m from another node through the network
	 * The method parses the message for the correct action
	 * 
	 * The first parameter of the message is the INSTRUCTION: NEIGHBORS, ELECT, LEADER, or FAILURE
	 * 
	 * For NEIGHBORS messages, the rest of the message is a list of neighbor ids
	 * Example: NEIGHBORS 5 6 7 where 5, 6, 7, are the ids of this node's neighbors
	 * 
	 * For ELECT messages, the next part of the message is either START which means 
	 * that the node is starting an election cycle. Or is it an id of another node
	 * that passed an election message to the current node
	 * Example: ELECT START or ELECT 8
	 * 
	 * For LEADER messages, the next part of the message is the id of the current leader
	 * Example: LEADER 5
	 * @param Message m
	 */
	public void processMsg() {
		while (!incomingMsg.isEmpty()) {
			String m = incomingMsg.removeFirst();
			if (!m.isEmpty()) {
				String[] parsed_message = m.split(" ");
				String instruction = parsed_message[0];
				if (instruction.equals("NEIGHBORS")) {
					for (int i = 1; i < parsed_message.length; i++) {
						addNeighbour(network.getNodeById(Integer.parseInt(parsed_message[i])));
					}
				}
				else if (instruction.equals("ELECT")) {				
					// Get next node to send to
					int next_node_id = getNextNodeIndex();
					Node next_node = network.getNodeById(next_node_id);
					String message = "";
					
					// Start Election Message
					if (parsed_message[1].equals("START")) { 
						message = "ELECT " + this.getNodeId();
						System.out.println("Round: " + network.getRound() +  this + " starting election");
					}
					// Election message from another node
					else {
						int message_id = Integer.parseInt(parsed_message[1]);
						if (message_id == this.getNodeId()) { // Same ids, start LEADER
							try {
								this.network.getBw().write("Round: " + network.getRound() + " LEADER " + getNodeId() + "\n");
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							System.out.println("Round: " + network.getRound() + " New Leader Elected: " + this);
							message = "LEADER " + this.getNodeId();
							leader = true;
							participant = false;
							this.node_leader = this;
							broadcast(message);
						}
						else if (message_id > this.getNodeId()) {
							message = "ELECT " + message_id;
						}
						else if (!participant && message_id < this.getNodeId()) {
							message = "ELECT " + this.getNodeId();
						}
					}

					if (this.myNeighbours.contains(next_node) && !message.isEmpty()) {
						System.out.println("Round: " + network.getRound() +  this + " sends msg: " + message + " to node: " + next_node);
						participant = true;				
						sendMsg(next_node_id, message);
					}
				}
				else if (instruction.equals("LEADER")) {
					int id = Integer.parseInt(parsed_message[1]);
					Node leader = network.getNodeById(id);
					this.node_leader = leader;
					if (participant == true) {
						broadcast(m);					
					}
					participant = false;
				}
				else if (instruction.equals("FAIL")) {
					try {
						network.getBw().write("FAIL " + this.getNodeId() + "\n");
					} catch (IOException e) {
						e.printStackTrace();
					}
					// If a leader fails, we start another election from the next node
					if (isNodeLeader()) {
						int next_node_id = getNextNodeIndex();
						sendMsg(next_node_id, "ELECT START");
					}
					// Remove this node from the list of nodes, update neighbors
					network.informNodeFailure(getNodeId());
				}
			}
		}
	}
		
	/**
	 * Helper method to get the node id of the next node in the ring
	 * @return id
	 */
	public int getNextNodeIndex() {
		Node node = this;
		int index_next = network.getIndexOfNode(node) + 1;
		if (index_next >= network.numNodes()) {
			index_next = 0;
		}
		Node next_node = network.getNodeByIndex(index_next);//		System.out.println(this + " " + next_node.getNodeId() + " " + index_next);
		if (next_node != null) {
			return next_node.getNodeId();
		}
		return 0;
	}
	
	/**
	 * Send a message to all of the neighbours
	 * @param m
	 */
	public void broadcast(String m) {
		for (Node node : myNeighbours) {
			int id = node.getNodeId();
			sendMsg(id, m);
		}
	}
	
	/**
	 * Sends a message
	 * The message must be delivered to its recipients through the network.
	 * This method need only implement the logic of the network receiving an outgoing message from a node.
	 * The remainder of the logic will be implemented in the network class.
	 * @param Message m
	 */
	public void sendMsg(int id, String m) {
		network.addMessage(id, m);
	}
	
	/**
	 * @return String representation of Node
	 */
	public String toString() {
		return " Node: " + id;
		
	}
}