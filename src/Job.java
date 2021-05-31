package src;

public class Job {
    // JOBN submitTime jobID estRuntime core memory disk
    
    private String type;
    private int submitTime;
    private int jobID;
    private int estRunTime;
    private int core;
    private int memory;
    private int disk;
            
    public Job(String state) {
        String[] temp = state.split(" ");
        
        this.type = temp[0];
        if (this.type.equals("JOBN")) {
            this.submitTime = Integer.parseInt(temp[1]);
            this.jobID = Integer.parseInt(temp[2]);
            this.estRunTime = Integer.parseInt(temp[3]);
            this.core = Integer.parseInt(temp[4]);
            this.memory = Integer.parseInt(temp[5]);
            this.disk = Integer.parseInt(temp[6]);
        }
    }

    public int getJobID() {
        return this.jobID;
    }

    public String getType() {
        return this.type;
    }

    public int getCoreUsage() {
        return this.core;
    }

    public int getMemoryUsage() {
        return this.memory;
    }

    public int getDiskUsage() {
        return this.disk;
    }
}