package vnet;

public record NodeId(int value) {

    public NodeId {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "Node ID must be positive"
            );
        }
    }
}
