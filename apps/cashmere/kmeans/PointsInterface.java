
import ibis.cashmere.WriteMethodsInterface;

public interface PointsInterface extends WriteMethodsInterface {
    public void generatePoints(int npoints, int nfeatures, int nTasks);
}
