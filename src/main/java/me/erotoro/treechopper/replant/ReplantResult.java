package me.erotoro.treechopper.replant;

public record ReplantResult(ReplantResultType type, int plantedCount, String detail) {
    public static ReplantResult skipped(ReplantResultType type, String detail) {
        return new ReplantResult(type, 0, detail);
    }

    public static ReplantResult planted(int count) {
        return new ReplantResult(ReplantResultType.PLANTED, count, "Planted " + count + " sapling(s).");
    }
}
