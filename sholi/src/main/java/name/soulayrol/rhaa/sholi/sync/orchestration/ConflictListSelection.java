package name.soulayrol.rhaa.sholi.sync.orchestration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;

public final class ConflictListSelection {

    private final ArrayList<String> orderedSyncIds = new ArrayList<String>();
    private final LinkedHashMap<String, ConflictSideSelection> selectionsBySyncId =
            new LinkedHashMap<String, ConflictSideSelection>();

    public static ConflictListSelection fromConflicts(List<SyncConflict> conflicts) {
        if (conflicts == null) {
            throw new IllegalArgumentException("conflicts must not be null");
        }
        ConflictListSelection selection = new ConflictListSelection();
        for (SyncConflict conflict: conflicts) {
            SyncConflictDisplayModel model = SyncConflictDisplayModel.from(conflict);
            selection.add(model.getSyncId(), model.canChooseLocal(), model.canChooseRemote());
        }
        return selection;
    }

    private ConflictListSelection() {
    }

    private void add(String syncId, boolean canChooseLocal, boolean canChooseRemote) {
        if (syncId == null || syncId.length() == 0) {
            throw new IllegalArgumentException("syncId must not be empty");
        }
        if (!selectionsBySyncId.containsKey(syncId)) {
            orderedSyncIds.add(syncId);
        }
        selectionsBySyncId.put(syncId, new ConflictSideSelection(canChooseLocal, canChooseRemote));
    }

    public boolean isComplete() {
        for (String syncId: orderedSyncIds) {
            ConflictSideSelection selection = selectionsBySyncId.get(syncId);
            if (selection == null || !selection.hasSelection()) {
                return false;
            }
        }
        return true;
    }

    public boolean select(String syncId, ConflictChoice choice) {
        ConflictSideSelection selection = selectionsBySyncId.get(syncId);
        return selection != null && selection.select(choice);
    }

    public boolean selectFromSwipeDelta(String syncId, float dx) {
        ConflictSideSelection selection = selectionsBySyncId.get(syncId);
        return selection != null && selection.selectFromSwipeDelta(dx);
    }

    public ConflictChoice getSelectedChoice(String syncId) {
        ConflictSideSelection selection = selectionsBySyncId.get(syncId);
        return selection == null ? null : selection.getSelectedChoice();
    }

    public List<SelectedChoice> choices() {
        ArrayList<SelectedChoice> choices = new ArrayList<SelectedChoice>();
        for (String syncId: orderedSyncIds) {
            ConflictSideSelection selection = selectionsBySyncId.get(syncId);
            ConflictChoice choice = selection == null ? null : selection.getSelectedChoice();
            if (choice != null) {
                choices.add(new SelectedChoice(syncId, choice));
            }
        }
        return choices;
    }

    public static final class SelectedChoice {
        private final String syncId;
        private final ConflictChoice choice;

        public SelectedChoice(String syncId, ConflictChoice choice) {
            if (syncId == null || syncId.length() == 0) {
                throw new IllegalArgumentException("syncId must not be empty");
            }
            if (choice == null) {
                throw new IllegalArgumentException("choice must not be null");
            }
            this.syncId = syncId;
            this.choice = choice;
        }

        public String getSyncId() {
            return syncId;
        }

        public ConflictChoice getChoice() {
            return choice;
        }
    }
}
