import java.io.*;
import java.net.*;
/** Master is a client. It makes requests to numWorkers.
 *   
 */
public class MasterSocket {
    static int maxServer = 16;
    static int[] tab_port = {25545,25546,25547,25548,25549,25550,25551,25552,25553,25554,25555,25556,25557,25558,25559,25560};
	static final String[] tab_ips = {
			"192.168.24.154", "192.168.24.150", "192.168.0.103", "192.168.0.104",
			"192.168.0.105", "192.168.0.106", "192.168.0.107", "192.168.0.108",
			"192.168.0.109", "192.168.0.110", "192.168.0.111", "192.168.0.112",
	};
    static String[] tab_total_workers = new String[maxServer];
    static BufferedReader[] reader = new BufferedReader[maxServer];
    static PrintWriter[] writer = new PrintWriter[maxServer];
    static Socket[] sockets = new Socket[maxServer];
    

    public static void main(String[] args) throws Exception {
		// MC parameters
		int totalCount = 16000000; // total number of throws on a Worker
		int numWorkers = maxServer;
		double pi;

		BufferedReader bufferRead = new BufferedReader(new InputStreamReader(System.in));
		String s; // for bufferRead

		System.out.println("#########################################");
		System.out.println("# Computation of PI by MC method        #");
		System.out.println("#########################################");

		System.out.println("\n How many workers for computing PI (< maxServer): ");
		try{
			s = bufferRead.readLine();
			numWorkers = Integer.parseInt(s);
			System.out.println(numWorkers);
		} catch(IOException ioE) {
			ioE.printStackTrace();
		}

		for (int i=0; i<numWorkers; i++){
			System.out.println("Enter worker"+ i +" IP : ");
			try {
				s = bufferRead.readLine();
				tab_ips[i] = s;
				System.out.println("You select " + s);
			} catch(IOException ioE) {
				ioE.printStackTrace();
			}
			System.out.println("Enter worker"+ i +" port : ");
			try {
				s = bufferRead.readLine();
				tab_port[i] = Integer.parseInt(s);
				System.out.println("You select " + s);
			} catch(IOException ioE) {
				ioE.printStackTrace();
			}
		}

		long stopTime, startTime;
		startTime = System.currentTimeMillis();
		pi = executeDistributedMonteCarlo(numWorkers, totalCount);
		stopTime = System.currentTimeMillis();

		System.out.println("\nPi : " + pi);
		System.out.println("Error: " + (Math.abs((pi - Math.PI)) / Math.PI) +"\n");

		System.out.println("Ntot: " + totalCount*numWorkers);
		System.out.println("Available processors: " + numWorkers);
		System.out.println("Time Duration (ms): " + (stopTime - startTime) + "\n");

		System.out.println( (Math.abs((pi - Math.PI)) / Math.PI) +" "+ totalCount*numWorkers +" "+ numWorkers +" "+ (stopTime - startTime));
	}

	public static double executeDistributedMonteCarlo(int numWorkers, int totalCount) throws IOException {
		int total = 0; // total number of throws inside quarter of disk

		//create worker's socket
        for(int i = 0 ; i < numWorkers ; i++) {
		    sockets[i] = new Socket(tab_ips[i], tab_port[i]);
		    System.out.println("SOCKET = " + sockets[i]);

		    reader[i] = new BufferedReader( new InputStreamReader(sockets[i].getInputStream()));
		    writer[i] = new PrintWriter(new BufferedWriter(new OutputStreamWriter(sockets[i].getOutputStream())),true);
        }

        String message_to_send;
        message_to_send = String.valueOf(totalCount / numWorkers);

		// initialize workers
		for(int i = 0 ; i < numWorkers ; i++) {
			writer[i].println(message_to_send);          // send a message to each worker
		}

		//listen to workers's message
		for(int i = 0 ; i < numWorkers ; i++) {
			tab_total_workers[i] = reader[i].readLine();      // read message from server
			System.out.println("Client sent: " + tab_total_workers[i]);
		}

		// compute PI with the result of each workers
		for(int i = 0 ; i < numWorkers ; i++) {
			total += Integer.parseInt(tab_total_workers[i]);
		}

        for(int i = 0 ; i < numWorkers ; i++) {
		    System.out.println("END");     // Send ending message
		    writer[i].println("END") ;
		    reader[i].close();
		    writer[i].close();
		    sockets[i].close();
        }

		return 4.0 * total / totalCount;
   }
}