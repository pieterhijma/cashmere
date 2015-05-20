package ibis.cashmere.many_core;

import java.util.List;
import java.nio.ByteBuffer;

public interface ZeroCopy {

    public void getByteBuffers(List<ByteBuffer> byteBuffers);
    public void setByteBuffers(List<ByteBuffer> byteBuffers);
}
