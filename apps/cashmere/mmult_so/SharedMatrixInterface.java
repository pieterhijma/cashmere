import ibis.cashmere.WriteMethodsInterface;

interface SharedMatrixInterface extends WriteMethodsInterface {
    void init(int task, int rec, int loop, float dbl, boolean flipped);
}
