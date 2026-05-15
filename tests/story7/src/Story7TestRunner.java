package story7;

public final class Story7TestRunner {

    private Story7TestRunner() {
    }

    public static void main(String[] args) throws Exception {
        MinSdkVersionStoryTest.run();
        CredentialStoreStoryTest.run();
        SecretSafetyStoryTest.run();
        System.out.println("Story #7 tests passed");
    }
}
