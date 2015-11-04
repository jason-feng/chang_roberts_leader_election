# Chang and Robert's Algorithm Implementation
###### Jason Feng
###### Distributed Systems - Fall 2015
###### November 4th, 2015


### Execution

./run.sh input.txt

### Description

This is a Java based stimulation of a multithreaded node based network that implements the Chang and Robert's algorithm for leader election. The network takes an input text file that describes the setup of the ring based network and also contains messages for leader election and node failure. Below I will describe the implementation of the various parts of my system.

### Input File Parsing

There are three types of line that we are parsing in our input message. 	
* Neighbor lines: Formatted with id and a list of neighbors
* Elect lines: Formatted with ELECT, round number, and a list of nodes that start leader election
* Fail lines: Formatted with FAIL, id of node that failed

We keep track of three different data structures to separate these messages. We keep track of a HashMap of messages to deliver to nodes. Each integer is the id of the node, and each string is the next message to be sent to that node. This map holds all of the neighbor initialization messages as well as the election messages and leader messages nodes pass to each other. We keep track of another HashMap of election messages that need to be sent for a specific round. Each integer is the round the message should be initialized, and each string is the list of ids of the nodes to start election. Finally we have a list of node ids that are the nodes that will fail. These are just in a list in order of the failure messages. 

### Ring Formation and Neighbor Initializaton

While we are parsing the input file, we know that if we reach an "ELECT" message that we ahve received all of the neighbor messages. When we find this first elect messages, we want to intiialize all of the neighbors immediately. This is because our HashMap of messages can only hold one message per id at a time. Therefore afterwards we immediately initialize the neighbors for each node.

The ring itself is formed during the parsing of the input file. The ring ordering is the same ordering as the lines in the input file that we read. Therefore after we finish parsing all of the "neighbor" lines in the input file, our ring will already be properly sorted. 

In the first rounds during this initialization, there is no round incrementing and counting.

After the neighbor initialization is finished, we can then begin by delievering the election messages. 

### Delivering Messages

Like the message parsing we break up message delivery into parts: the election messages, and then the failure messages. 

For each round, we make sure the round only lasts for the given period of time. We check if we have a round message for election, if we do, then we are parse that message and start leader election for those nodes. 

After we check for round messages, then we check our map of messages to deliver and we deliver each of those messages to their respective nodes. The message parsing is done within the node class.

Part of the system design is to make sure that nodes send messages only to its neighbors as specified in the input file. Per round, one node is allowed to send one message to each neighbor. We enforce this through our HashMap of messags. Since a HashMap stores unique copies of messages per node, we can send at most one message per node. 

If in the same round a node has more than one messages to send to the same neighbour, we have a queue of messages in each node. When adding messages from the nodes to the network, we check if a message to that node has already been sent, if it has, then we add it to the queue. Later in that round, we process all of the messages in that queue. 

A message between two nodes has to pass through the network. This means that a node send a message directly to its neighbours. This is to enforcement the one message per neighbor per round policy and also to keep track of the rounds that have passed. 

If we are done with all of the elections, we start sending the failure messages. 

### Termination Detection

When all of our data structures holding messages are empty, then we know there are no more messages to be sent, therefore our network can stop delivering messages. 

### Message Parsing

The message parsing is completed in the Node class when each individual node receives a message. The messages are designed as such:

* The first parameter of the message is the INSTRUCTION: NEIGHBORS, ELECT, LEADER, or FAILURE
 * For NEIGHBORS messages, the rest of the message is a list of neighbor ids
 * Example: NEIGHBORS 5 6 7 where 5, 6, 7, are the ids of this node's neighbors
 

 * For ELECT messages, the next part of the message is either START which means 
 * that the node is starting an election cycle. Or is it an id of another node
 * that passed an election message to the current node
 * Example: ELECT START or ELECT 8
 

 * For LEADER messages, the next part of the message is the id of the current leader
 * Example: LEADER 5
 
When a node receives a neighbor message, it retrieves each node according to their id from the network and adds all of these nodes as its neighbors.

When a node receives an elect message, this is when we implement the leader election algorithm which will be described below.

When a node receives a leader message, then the node knows that a leader has been elected. The node sets itself to be a non-participant. If the node was previously a participant, that means the node has not yet received this new leader message, so it broadcasts the same message to all of its neighbors. If we declare a new leader, we also need to make sure the old leader knows its no longer a leader, this check is actually unncessary since the only way for a leader to be replaced is for the original leader to be removed.

When a node receives a failure message, it will begin the failure process which is also described below. 

### Chang and Robert's Leader Algorithm

The algorithm is implemented as such in the node class. If we receive a start election message, then we send an election message to the next node in the ring. 

If we receive an election message from another node, we compare the node id in the election message to our own node id. There are three different cases we need to consider:

* If the node ids are the same, then we declare ourselves the new leader, write to our output file, and broadcast this message to all of our neighbors.
* If the node id in the message is greater than our id, then we pass on the same message to the next node
* If we are not yet a participant and the node id in the message is less than our id, we pass on an election message with our node id to the next node

Whenever we start an election or pass an election message, we always mark ourselves as a participant. After an election is complete, does the new leader lets everyone know that it is the current leader to stop the election cycle. 

### Node Failures

When a node receives a failure message, it checks if the node is the current leader. If it is a leader, then we start another election starting from the next node in the ring. 

Regardless of whether or not this node is leader, we have to inform the network that this node has failed. The network will then broadcast to all of the neighbors of the failed node to remove the failed node from their list of neighbors. The network itself also removes the failed node from its list of nodes

Since we keep our nodes in a doubly linked list, by removing node, we can keep the ring formation by simply shifting the pointers in the list. Therefore we keep the integrity of our ring formation. 


