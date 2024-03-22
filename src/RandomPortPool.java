import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;

public class RandomPortPool {
    // If can't acquire by time out.
    public static class Timeout extends Exception {
        public Timeout(String message) {
            super(message);
        }
    }

    private List<Integer> ports;
    private boolean sync;
    private int limit;
    private Object monitor;

    // Ctor.
    // @param [bool] Set it to FALSE if you want this pool to be NOT thread-safe
    // @param [int] Set the maximum number of ports in the pool
    public RandomPortPool(boolean sync, int limit) {
        ports = new ArrayList<>();
        this.sync = sync;
        this.limit = limit;
        monitor = new Object();
    }

    // Application wide pool of ports
    public static final RandomPortPool SINGLETON = new RandomPortPool(true, 65_536);

    // How many ports acquired now?
    public int count() {
        return ports.size();
    }

    // Is it empty?
    public boolean empty() {
        return ports.isEmpty();
    }

    // Acquire a new random TCP port.
    //
    // You can specify the number of ports to acquire. If it's more than one,
    // an array will be returned.
    //
    // You can specify the amount of seconds to wait until a new port
    // is available.
    public int[] acquire(int total, int timeout) throws Timeout {
        long start = System.currentTimeMillis();
        while (true) {
            if (System.currentTimeMillis() > start + timeout * 1000) {
                throw new Timeout("Can't find a place in the pool of " + limit + " ports for " + total + " port(s), in " + String.format("%.02f", (System.currentTimeMillis() - start) / 1000.0) + "s");
            }
            int[] opts = safe(() -> {
                if (ports.size() + total > limit) {
                    return null;
                }
                opts = new int[total];
                try {
                    for (int i = 0; i < total; i++) {
                        opts[i] = i == 0 ? take() : take(opts[i - 1] + 1);
                    }
                } catch (SocketException e) {
                    return null;
                }
                if (opts == null || ports.stream().anyMatch(p -> p == opts[i])) {
                    return null;
                }
                int d = total * (total - 1) / 2;
                if (opts.stream().mapToInt(Integer::intValue).sum() - (total * opts[0]) == d) {
                    ports.addAll(opts);
                    return opts;
                }
                return null;
            });
            if (opts == null) {
                continue;
            }
            if (total == 1) {
                return opts;
            }
            if (total > 1 && opts.length == 1) {
                return new int[]{opts[0]};
            }
            if (total > 1 && opts.length > 1) {
                return opts;
            }
        }
    }

    // Return it/them back to the pool.
    public void release(int port) throws IOException {
        safe(() -> ports.removeIf(p -> p == port));
    }

    private int take() throws IOException {
        ServerSocket server = new ServerSocket(0);
        int p = server.getLocalPort();
        server.close();
        return p;
    }

    private int take(int opt) throws IOException {
        ServerSocket server = new ServerSocket(opt);
        int p = server.getLocalPort();
        server.close();
        return p;
    }

    private <T> T safe(SafeBlock<T> block) throws IOException {
        if (sync) {
            synchronized (monitor) {
                return block.execute();
            }
        } else {
            return block.execute();
        }
    }

    private interface SafeBlock<T> {
        T execute() throws IOException;
    }
}

