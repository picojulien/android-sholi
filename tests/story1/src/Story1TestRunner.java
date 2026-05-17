package story1;

public final class Story1TestRunner {

    private Story1TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        ItemSyncMetadataStoryTest.run();
        ItemSyncMigrationStoryTest.run();
        ItemAddResolutionStoryTest.run();
        System.out.println("Story #1 tests passed");
    }
}
