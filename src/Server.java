package src;

public class Server {
    public static final String STATE_ACTIVE = "active";
    public static final String STATE_INACTIVE = "inactive";
    public static final String STATE_IDLE = "idle";

    // serverType serverID state curStartTime core mem disk #wJobs #rJobs [#failures totalFailtime mttf mttr madf lastStartTime]
    // e.g. joon 1 inactive -1 4 16000 64000 0 0

    private String serverType;
    private int serverID;
    private String state;
    private int curStartTime;
    private int cores;
    private int memory;
    private int disk;
    private int wJobs;
    private int rJobs;

    public Server(String serverState) {
        String[] temp = serverState.split(" ");

        this.serverType = temp[0];
        this.serverID = Integer.parseInt(temp[1]);
        this.state = temp[2];
        this.curStartTime = Integer.parseInt(temp[3]);
        this.cores = Integer.parseInt(temp[4]);
        this.memory = Integer.parseInt(temp[5]);
        this.disk = Integer.parseInt(temp[6]);
        this.wJobs = Integer.parseInt(temp[7]);
        this.rJobs = Integer.parseInt(temp[8]);
    }

    public String getServerType() {
        return this.serverType;
    }

    public int getServerID() {
        return this.serverID;
    }

    public String getState() {
        return this.state;
    }

    public int getNumberOfCores() {
        return this.cores;
    }

    public int getMemoryUsage() {
        return this.memory;
    }

    public int getDiskUsage() {
        return this.disk;
    }

    public void printServer() {
        System.out.println(this.serverType + " " + this.serverID + " " + this.state + " " + this.cores);
    }
}