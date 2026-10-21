package name.soulayrol.rhaa.sholi.sync.orchestration;

import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;

public final class ConflictSideSelection {

    private final boolean canChooseLocal;
    private final boolean canChooseRemote;
    private ConflictChoice selectedChoice;

    public ConflictSideSelection(boolean canChooseLocal, boolean canChooseRemote) {
        this.canChooseLocal = canChooseLocal;
        this.canChooseRemote = canChooseRemote;
        if (canChooseLocal && !canChooseRemote) {
            selectedChoice = ConflictChoice.LOCAL;
        } else if (!canChooseLocal && canChooseRemote) {
            selectedChoice = ConflictChoice.REMOTE;
        }
    }

    public boolean canChooseLocal() {
        return canChooseLocal;
    }

    public boolean canChooseRemote() {
        return canChooseRemote;
    }

    public boolean hasSelection() {
        return selectedChoice != null;
    }

    public ConflictChoice getSelectedChoice() {
        return selectedChoice;
    }

    public boolean selectLocal() {
        return select(ConflictChoice.LOCAL);
    }

    public boolean selectRemote() {
        return select(ConflictChoice.REMOTE);
    }

    public boolean select(ConflictChoice choice) {
        if (choice == ConflictChoice.LOCAL) {
            if (!canChooseLocal) {
                return false;
            }
        } else if (choice == ConflictChoice.REMOTE) {
            if (!canChooseRemote) {
                return false;
            }
        } else {
            return false;
        }
        selectedChoice = choice;
        return true;
    }

    public boolean selectFromSwipeDelta(float dx) {
        if (dx < 0) {
            return selectLocal();
        }
        if (dx > 0) {
            return selectRemote();
        }
        return false;
    }
}
