package src;

public class Tuple {
    // JOBN submitTime jobID estRuntime core memory disk
    
    public String x;
    public int y;
            
    public Tuple(String x, int y) {
        this.x = x;
        this.y = y;
    }

    public String getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }
}