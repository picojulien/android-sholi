package name.soulayrol.rhaa.sholi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Toast;

import java.util.List;

import name.soulayrol.rhaa.sholi.sync.android.AndroidWebDavSyncRunner;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.orchestration.SyncConflictDisplayModel;

public final class SyncConflictDialogFragment extends DialogFragment {

    private static final String TAG = "sync_conflict_dialog";

    private SyncConflict conflict;

    public static void showNext(FragmentManager fragmentManager) {
        if (fragmentManager == null) {
            return;
        }
        if (fragmentManager.findFragmentByTag(TAG) != null) {
            return;
        }
        new SyncConflictDialogFragment().show(fragmentManager, TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        List<SyncConflict> conflicts = new AndroidWebDavSyncRunner(activity).loadUnresolvedConflicts();
        if (conflicts.isEmpty()) {
            return new AlertDialog.Builder(activity)
                    .setTitle(R.string.sync_conflicts_resolved_title)
                    .setMessage(R.string.sync_conflicts_resolved_message)
                    .setPositiveButton(android.R.string.ok, null)
                    .create();
        }

        conflict = conflicts.get(0);
        SyncConflictDisplayModel model = SyncConflictDisplayModel.from(conflict);
        String message = model.toDisplayText();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.sync_conflict_dialog_title)
                .setMessage(message)
                .setNeutralButton(android.R.string.cancel, null);
        if (model.canChooseLocal()) {
            builder.setPositiveButton("Use local", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        choose(ConflictChoice.LOCAL);
                    }
                });
        }
        if (model.canChooseRemote()) {
            builder.setNegativeButton("Use remote", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        choose(ConflictChoice.REMOTE);
                    }
                });
        }
        return builder.create();
    }

    private void choose(ConflictChoice choice) {
        Activity activity = getActivity();
        if (activity == null || conflict == null) {
            return;
        }
        AndroidWebDavSyncRunner runner = new AndroidWebDavSyncRunner(activity);
        try {
            runner.choose(conflict.getSyncId(), choice);
        } catch (IllegalStateException e) {
            Toast.makeText(activity, e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        if (runner.loadUnresolvedConflicts().isEmpty()) {
            Toast.makeText(
                    activity,
                    activity.getString(R.string.sync_conflicts_resolved_message),
                    Toast.LENGTH_LONG).show();
        } else {
            dismissAllowingStateLoss();
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    showNext(activity.getFragmentManager());
                }
            });
        }
    }
}
