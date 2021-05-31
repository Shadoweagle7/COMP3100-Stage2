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

                    send(dout, "SCHD " + current.getJobID() + " " + selected.getX() + " " + selected.getY());

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
        //System.out.println("Sending " + toSend);
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

        //System.out.println("Server: " + new String(data));

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
        //System.out.println("Server: " + receivedString);
        
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

        ArrayList<Server> compatibleServers = (ArrayList<Server>)servers.stream().filter(
            (server) -> {
                float coresUsed = 0.0f;

                try {
                    send(dout, "LSTJ " + server.getServerType() + " " + server.getServerID());

                    byte[] received = new byte[12];

                    String data = receive(received, 12, din); // DATA nRecs recLen

                    String[] dataNRecsRecLen = data.split(" "); // According to current implementation [1] is always going to be 1

                    int recLen = Integer.parseInt(dataNRecsRecLen[2]);
                    received = new byte[recLen];

                    send(dout, "OK");

                    String stringReceived = receive(received, recLen, din);

                    ArrayList<String[]> allJobs = new ArrayList<String[]>();

                    while (!stringReceived.equals(".")) {
                        // System.out.println(stringReceived);

                        String[] jobDetails = stringReceived.split(" ");

                        allJobs.add(jobDetails);

                        // Integer.parseInt(jobDetails[4]);

                        send(dout, "OK");

                        stringReceived = receive(received, recLen, din);
                    }

                    for (int i = 0; i < allJobs.size(); i++) {
                        // 1. Get start and end time
                        // 2. See if it overlaps with any other start and end time. Iff it does, add it
                        //    to coreCount.

                        int startTime = Integer.parseInt(allJobs.get(i)[2]);
                        int estEndTime = Integer.parseInt(allJobs.get(i)[3]);
                        int cores = Integer.parseInt(allJobs.get(i)[4]);

                        for (int j = 0; j < allJobs.size(); j++) {
                            if (i != j) {
                                int startTimeOtherJob = Integer.parseInt(allJobs.get(i)[2]);
                                int estEndTimeOtherJob = Integer.parseInt(allJobs.get(i)[3]);

                                if (
                                    (
                                        startTime < startTimeOtherJob &&
                                        startTimeOtherJob < estEndTime
                                    ) ||
                                    (
                                        startTimeOtherJob < startTime &&
                                        estEndTime < estEndTimeOtherJob
                                    )
                                ) { // Overlap detected
                                    coresUsed += (cores) / 2.0f; // This algorithm will cause it to add twice, so divide by half to
                                                                 // compensate.
                                }
                            }
                        }
                    }

                    //System.out.println("coresUsed: " + coresUsed);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(0);
                }

                return
                    server.getNumberOfCores() >= (coreCount - (int)coresUsed) &&
                    server.getMemoryUsage() >= memory &&
                    server.getDiskUsage() >= disk;
            }
        ).collect(Collectors.toList());

        sType = compatibleServers.get(0).getServerType();
        sID = compatibleServers.get(0).getServerID();

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
