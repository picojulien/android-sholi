package story2;

public final class Story2TestRunner {

    private Story2TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        SyncDocumentSerializationStoryTest.run();
        SyncDocumentParsingStoryTest.run();
        SyncDocumentAdapterStoryTest.run();
        System.out.println("Story #2 tests passed");
    }
}
