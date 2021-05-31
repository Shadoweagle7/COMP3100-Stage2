import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import src.Server;
import src.Data;
import src.Job;
import src.Tuple;

public class Client {
    public static void printErrorMessage(String expected, String actual) {
        System.out.println("Expected: " + expected + " | Actual: " + actual);
    }

    public static void printErrorMessage(int expected, int actual) {
        System.out.println("Expected: " + expected + " | Actual: " + actual);
    }


    public static void main(String[] args) throws Exception {
        // Using Java SE 7's Automatic Resource Management to call close() for us, since these objects implement the AutoCloseable interface.
        // This works even if any of the code throws an exception.
        try (
            Socket s = new Socket("localhost", 50000);
            DataInputStream din = new DataInputStream(s.getInputStream());
            DataOutputStream dout = new DataOutputStream(s.getOutputStream());
        ) {
            ArrayList<Job> jobs = new ArrayList<Job>();
            
            byte[] received = new byte[32];

            send(dout, "HELO");
        
            receive(received, "OK", din);

            send(dout, "AUTH shadoweagle7");
            
            receive(received, "OK", din);

            send(dout, "REDY");

            String job = receive(received, 64, din); // JOBN ...
            jobs.add(new Job(job));

            send(dout, "GETS All");

            String rawDataString = receive(received, 12, din); // DATA nRecs recLen

            Data dataCommand = (Data)parseCommand(rawDataString);

            int[] dataBytes = dataCommand.execute();
            int totalDataSize = dataBytes[0] * dataBytes[1];

            send(dout, "OK");

            received = new byte[totalDataSize];
            String serversString = receive(received, totalDataSize, din);

            send(dout, "OK");

            receive(received, ".", din);

            //System.out.println(new String(received));

            // The magic infinite debug loop
            // int i = 0; while (i < Integer.MAX_VALUE) { i++; }

            // Build servers array
            ArrayList<Server> servers = new ArrayList<Server>();
            String serverStateAll = new String(serversString);
            String[] serverStates = serverStateAll.split("\n");

            for (String serverState : serverStates) {
                servers.add(new Server(serverState));
            }

            // Filter servers
            servers.removeIf((server) -> {
                return !(server.getState().equals(Server.STATE_IDLE) || server.getState().equals(Server.STATE_INACTIVE));
            });

            // Sort servers
            servers.sort((Server l, Server r) -> {
                if (l.getNumberOfCores() > r.getNumberOfCores()) {
                    return -1;
                } else if (l.getNumberOfCores() < r.getNumberOfCores()) {
                    return 1;
                }

                return 0;
            });

            Job current = jobs.get(0);
            
            while (!current.getType().equals("NONE")) {
                if (current.getType().equals("JOBN")) {
                    Tuple selected = selectServer(current, servers, din, dout);

                    send(dout, "SCHD " + current.getJobID() + " " + selected.x + " " + selected.y);

                    receive(received, 64, din);

                    send(dout, "REDY");
                } else if (current.getType().equals("JCPL")) {
                    send(dout, "REDY");
                }

                current = new Job(receive(received, 64, din));
            }
            
            //send(dout, "OK");

            send(dout, "QUIT");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void send(DataOutputStream dout, String toSend) throws IOException {
        System.out.println("Sending " + toSend);
        dout.write(toSend.getBytes());
        dout.flush();
    }

    public static String receive(byte[] data, int expectedSize, DataInputStream din) throws IOException {
        data = new byte[expectedSize];
        if (din.read(data) >= expectedSize) {
            printErrorMessage(expectedSize, data.length);
            //System.out.println("Server: " + new String(data, 0, expectedSize));
            //throw new IllegalArgumentException("Server gave unexpected response");
        }

        System.out.println("Server: " + new String(data));

        return new String(data).trim();
    }

    public static String receive(byte[] data, String expectedString, DataInputStream din) throws IOException, IllegalArgumentException {
        int expectedSize = expectedString.length();
        data = new byte[expectedSize];

        if (din.read(data) >= expectedSize) {
            //System.out.println("Server: " + new String(data, 0, expectedSize));
            //throw new IllegalArgumentException("Server gave unexpected response");
        }

        String receivedString = new String(data, 0, expectedSize).trim();
        System.out.println("Server: " + receivedString);
        
        if (!receivedString.equals(expectedString)) {
            printErrorMessage(expectedString, receivedString);

            throw new IllegalArgumentException("Server sent unknown message");
        }

        return receivedString;
    }

    public static Tuple selectServer(Job current, ArrayList<Server> servers, DataInputStream din, DataOutputStream dout) throws IOException {
        String sType = "lol";
        int sID = 0;

        final int coreCount = current.getCoreUsage();
        final int memory = current.getMemoryUsage();
        final int disk = current.getDiskUsage();

        // FF

        ArrayList<Server> compatibleServers = (ArrayList<Server>)servers.stream().filter(
            (server) -> (
                server.getNumberOfCores() >= coreCount 
                //&&
                //server.getMemoryUsage() >= memory &&
                //server.getDiskUsage() >= disk
            )
        ).collect(Collectors.toList());

        sType = compatibleServers.get(0).getServerType();
        sID = compatibleServers.get(0).getServerID();

        /*
        
         - Minimization of average turnaround time (Hybrid FF / BF)

            Setup: List of servers. A number of threads prepared to run to scan the list
                   of servers available. Spread out the list of jobs equally between threads
                   such that they never overlap. Never edit this list (so that a mutex
                   is not required). The number of threads spawned should increase based
                   on the size of the list of servers (spawned on the main thread if there
                   aren't that many on the list).

                   Score Value for each server.

            1. For each server:
                a) Check if the server has the resources to run the job (core, memory, disk)
                    i) Use CNTJ to check how many jobs the server is running. If there are
                       too many jobs, skip this server. What is defined as "too many" can
                       be inputted by the user on the command line.

                       CNTJ serverType serverID jobState

                       E.g.

                       CNTJ joon 0 2 would query how many running jobs there are on joon 0
                    ii) Get the core count, memory and disk usage.
                b) Check for the following:
                    i) If there is enough cores available, it can run the job in parallel.
                       LSTJ -> jobID jobState startTime estRunTime core memory disk

                       Use LSTJ to get the list of all jobs and find the minimum core usage at
                       any given time. By checking the startTime and estRunTime of each job, we
                       can attempt to find a timespan in which there is enough cores on the server
                       available to run the job.
                    ii) If there is enough cores, memory and disk (total installed on the system),
                        it can run the job.

                    If the server satisfies i), it gains a +1 to its Score Value. If it
                    satisfies ii), it gains a +2 to its Score Value. Servers with a Score
                    of 1 can run the job in parallel, but do not have enough memory and
                    disk to complete the job. Servers with a Score of 2 have enough memory
                    and disk resources installed total to run the job, but cannot immediately
                    run the job (since it cannot run it in parallel). Servers that have a
                    Score of 3 satisfy both conditions and are ideal for execution. The first
                    server that is identified to have a Score of 3 will be returned immediately,
                    immediately going to c).
                c) Join all threads (wait for execution to finish)
            2. If a server is identified by step 1. b), then return this server. Otherwise, follow
               the below steps.

               At this point, servers in the eligible list can only have a score of 1 or 2.

               The user can input a maximum time to wait for the job to complete. The next server with
               a Score of 2 should be returned. If there are no servers with a Score of 2, we
               need to migrate a job from a server with a Score of 2 or 3.

               MIGJ -> jobID srcServerType srcServerID tgtServerType tgtServerID.

               In the worst case scenario where no server can be optimized for usage,
               jobs may have to be migrated from servers to make room. First, send a warning to the
               user mentioning that no servers are available to take the request for the job (
               "There are currently no servers available to process Job <Job Details>. Waiting
               for an available server..."). Then MIGJ can be used to migrate some jobs.
            
               a) Gather all servers with a Score Value of 2.
               b) Find the server with the highest Score
               c) Run LSTJ on that server to get the jobs it is running.
               d) Migrate a job from a server with a Score of 2 to the server with the highest Score
                  Ensure that the job migrated has equal or greater resource requirements than the
                  job to be submitted.
               e) Schedule our job onto the job which had a job migrated from it.

        */

        

        return new Tuple(sType, sID);
    }

    public static Data parseCommand(String str) 
        throws NumberFormatException, ArrayIndexOutOfBoundsException, IllegalArgumentException, NullPointerException {
        if (str == null || str.length() == 0) {
            throw new NullPointerException("Invalid input");
        }

        String[] args = str.split(" ");

        if (args == null || args.length == 0) {
            throw new ArrayIndexOutOfBoundsException("Invalid command");
        }

        if (args[0].equals("DATA")) {
            int numberOfRecords = Integer.parseInt(args[1]), recordLength = Integer.parseInt(args[2]);

            return new Data(numberOfRecords, recordLength);
        }

        throw new IllegalArgumentException("Invalid command");
    }
}
