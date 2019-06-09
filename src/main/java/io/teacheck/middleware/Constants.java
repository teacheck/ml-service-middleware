package io.teacheck.middleware;

public final class Constants {

    public final static int SERVER_PORT = Integer.parseInt(System.getenv("SERVER_PORT"));
    public final static int ML_SERVICE_PORT = Integer.parseInt(System.getenv("ML_SERVICE_PORT"));
    public final static int NUM_PREDICTIONS = Integer.parseInt(System.getenv("NUM_PREDICTIONS"));
    public final static int NUM_THREADS = Integer.parseInt(System.getenv("NUM_THREADS"));
    public final static String ML_SERVICE_HOST = System.getenv("ML_SERVICE_HOST");

}
