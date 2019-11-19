package sample.rpcfy;

public class CustomException2Arg extends Exception {

    public CustomException2Arg(int a, int b) {
        super("CustomException");
    }
}
