package vnet;

public enum ProbeType {

    REQUEST(1),
    RESPONSE(2);

    private final int wireValue;

    ProbeType(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    /**
     * Converts a protocol-defined numeric packet type into a ProbeType.
     *
     * Uses explicit wire values rather than enum ordinals so that
     * reordering enum constants does not change the binary protocol.
     *
     * @param value the unsigned packet type value from the wire
     * @return the corresponding ProbeType
     * @throws IllegalArgumentException if the value is unknown
     */
    public static ProbeType fromWireValue(int value) {
        for (ProbeType type : values()) {
            if (type.wireValue() == value) {
                return type;
            }
        }

        throw new IllegalArgumentException(
                "Unknown probe type: " + value
        );
    }
}