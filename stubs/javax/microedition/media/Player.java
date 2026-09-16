package javax.microedition.media;
import java.io.IOException;
public interface Player extends Controllable {
    int UNREALIZED = 100, REALIZED = 200, PREFETCHED = 300, STARTED = 400, CLOSED = 0;
    long TIME_UNKNOWN = -1L;
    void realize() throws MediaException;
    void prefetch() throws MediaException;
    void start() throws MediaException;
    void stop() throws MediaException;
    void deallocate();
    void close();
    long setMediaTime(long now) throws MediaException;
    long getMediaTime();
    long getDuration();
    int getState();
    void setLoopCount(int count);
    String getContentType();
    void addPlayerListener(PlayerListener playerListener);
    void removePlayerListener(PlayerListener playerListener);
}
