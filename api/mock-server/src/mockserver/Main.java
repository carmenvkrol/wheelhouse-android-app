package mockserver;

public final class Main {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8787;
        new ApiServer().start(port);
    }
}
