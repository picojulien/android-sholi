package name.soulayrol.rhaa.sholi;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import name.soulayrol.rhaa.sholi.data.ItemViewStyler;
import name.soulayrol.rhaa.sholi.sync.android.AndroidWebDavSyncRunner;
import name.soulayrol.rhaa.sholi.sync.merge.ConflictChoice;
import name.soulayrol.rhaa.sholi.sync.merge.SyncConflict;
import name.soulayrol.rhaa.sholi.sync.orchestration.ConflictListSelection;
import name.soulayrol.rhaa.sholi.sync.orchestration.SyncConflictDisplayModel;
import name.soulayrol.rhaa.sholi.sync.webdav.WebDavSyncResult;

public final class SyncConflictDialogFragment extends DialogFragment {

    private static final String TAG = "sync_conflict_dialog";
    private static final String ARG_DIAGNOSTIC_MESSAGE = "diagnostic_message";

    private final ArrayList<ConflictRowViews> conflictRows = new ArrayList<ConflictRowViews>();
    private Button confirmButton;
    private ConflictListSelection selection;

    public static void showNext(FragmentManager fragmentManager) {
        showNext(fragmentManager, null);
    }

    public static boolean isShowing(FragmentManager fragmentManager) {
        return fragmentManager != null && fragmentManager.findFragmentByTag(TAG) != null;
    }

    public static void showNext(FragmentManager fragmentManager, String diagnosticMessage) {
        if (fragmentManager == null) {
            return;
        }
        if (isShowing(fragmentManager)) {
            return;
        }
        Bundle arguments = new Bundle();
        arguments.putString(ARG_DIAGNOSTIC_MESSAGE, diagnosticMessage);
        SyncConflictDialogFragment fragment = new SyncConflictDialogFragment();
        fragment.setArguments(arguments);
        fragment.show(fragmentManager, TAG);
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

        selection = ConflictListSelection.fromConflicts(conflicts);
        View view = LayoutInflater.from(activity).inflate(R.layout.sync_conflict_dialog, null);
        bindDialogView(activity, view, conflicts, diagnosticMessage());

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.sync_conflict_dialog_title)
                .setView(view)
                .setPositiveButton(R.string.sync_conflict_use_selection, null)
                .setNeutralButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                confirmButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                confirmButton.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (selection != null && selection.isComplete()) {
                            resolveSelections(selection.choices());
                        }
                    }
                });
                updateSelectedRows();
            }
        });
        return dialog;
    }

    private void bindDialogView(
            final Activity activity,
            View view,
            List<SyncConflict> conflicts,
            String diagnosticMessage) {
        bindDiagnosticMessage(activity, view, diagnosticMessage);
        LinearLayout conflictList = (LinearLayout) view.findViewById(R.id.sync_conflict_list);
        LayoutInflater inflater = LayoutInflater.from(activity);
        conflictRows.clear();
        for (SyncConflict conflict: conflicts) {
            SyncConflictDisplayModel model = SyncConflictDisplayModel.from(conflict);
            View row = inflater.inflate(R.layout.sync_conflict_row, conflictList, false);
            bindConflictRow(activity, row, model);
            conflictList.addView(row);
        }
        updateSelectedRows();
    }

    private void bindDiagnosticMessage(Activity activity, View view, String diagnosticMessage) {
        TextView diagnosticView = (TextView) view.findViewById(R.id.sync_conflict_diagnostic_message);
        if (diagnosticView == null) {
            return;
        }
        String message = diagnosticMessage == null ? null : diagnosticMessage.trim();
        if (message == null || message.length() == 0) {
            diagnosticView.setVisibility(View.GONE);
            return;
        }
        diagnosticView.setText(activity.getString(R.string.sync_conflict_diagnostic_label)
                + "\n" + message);
        diagnosticView.setVisibility(View.VISIBLE);
    }

    private String diagnosticMessage() {
        Bundle arguments = getArguments();
        if (arguments == null) {
            return null;
        }
        return arguments.getString(ARG_DIAGNOSTIC_MESSAGE);
    }

    private void bindConflictRow(final Activity activity, View row, SyncConflictDisplayModel model) {
        final String syncId = model.getSyncId();
        TextView changedFields = (TextView) row.findViewById(R.id.sync_conflict_changed_fields);
        changedFields.setText(activity.getString(R.string.sync_conflict_changed_fields_label)
                + ": " + model.getChangedFields());

        View localColumn = row.findViewById(R.id.sync_conflict_local_column);
        View remoteColumn = row.findViewById(R.id.sync_conflict_remote_column);
        conflictRows.add(new ConflictRowViews(syncId, localColumn, remoteColumn));
        bindConflictSide(
                activity,
                localColumn,
                R.id.sync_conflict_local_item,
                R.id.sync_conflict_local_new,
                R.id.sync_conflict_local_details,
                model.getLocal(),
                model.isLocalNew(),
                model.canChooseLocal());
        bindConflictSide(
                activity,
                remoteColumn,
                R.id.sync_conflict_remote_item,
                R.id.sync_conflict_remote_new,
                R.id.sync_conflict_remote_details,
                model.getRemote(),
                model.isRemoteNew(),
                model.canChooseRemote());

        localColumn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSelectedSide(syncId, ConflictChoice.LOCAL);
            }
        });
        remoteColumn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setSelectedSide(syncId, ConflictChoice.REMOTE);
            }
        });
        configureSwipeSelection(activity, row, syncId, localColumn, remoteColumn);
    }

    private void bindConflictSide(
            Activity activity,
            View column,
            int itemViewId,
            int newIndicatorId,
            int detailsViewId,
            SyncConflictDisplayModel.Side side,
            boolean newSide,
            boolean canChoose) {
        TextView newIndicator = (TextView) column.findViewById(newIndicatorId);
        View itemView = column.findViewById(itemViewId);
        TextView itemName = (TextView) itemView.findViewById(R.id.item_name);
        TextView details = (TextView) column.findViewById(detailsViewId);

        newIndicator.setVisibility(newSide ? View.VISIBLE : View.INVISIBLE);
        column.setEnabled(canChoose);
        column.setClickable(canChoose);
        column.setAlpha(canChoose ? 1.0f : 0.45f);

        if (side.isPresent()) {
            ItemViewStyler.bindName(itemName, displayValue(side.getName()), side.getStatus());
            details.setText(sideDetails(activity, side));
        } else {
            ItemViewStyler.bindName(
                    itemName,
                    activity.getString(R.string.sync_conflict_missing_side),
                    null);
            details.setText(activity.getString(R.string.sync_conflict_missing_side));
        }
    }

    private void configureSwipeSelection(
            Activity activity,
            View row,
            final String syncId,
            View localColumn,
            View remoteColumn) {
        ViewConfiguration configuration = ViewConfiguration.get(activity);
        final int minimumDistance = configuration.getScaledTouchSlop() * 4;
        final int minimumVelocity = configuration.getScaledMinimumFlingVelocity();
        final boolean[] swipeHandled = new boolean[] { false };
        final GestureDetector gestureDetector = new GestureDetector(
                activity,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onFling(
                            MotionEvent start,
                            MotionEvent end,
                            float velocityX,
                            float velocityY) {
                        if (start == null || end == null) {
                            return false;
                        }
                        float dx = end.getX() - start.getX();
                        float dy = end.getY() - start.getY();
                        if (Math.abs(dx) < minimumDistance
                                || Math.abs(dx) <= Math.abs(dy)
                                || Math.abs(velocityX) < minimumVelocity) {
                            return false;
                        }
                        swipeHandled[0] = true;
                        setSelectedSideFromSwipeDelta(syncId, dx);
                        return true;
                    }
                });
        View.OnTouchListener listener = new View.OnTouchListener() {
            @Override
            public boolean onTouch(View touchedView, MotionEvent event) {
                gestureDetector.onTouchEvent(event);
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_UP && swipeHandled[0]) {
                    swipeHandled[0] = false;
                    return true;
                }
                if (action == MotionEvent.ACTION_CANCEL) {
                    swipeHandled[0] = false;
                }
                return false;
            }
        };
        row.setOnTouchListener(listener);
        localColumn.setOnTouchListener(listener);
        remoteColumn.setOnTouchListener(listener);
    }

    private void setSelectedSide(String syncId, ConflictChoice choice) {
        if (selection == null || !selection.select(syncId, choice)) {
            return;
        }
        updateSelectedRows();
    }

    private void setSelectedSideFromSwipeDelta(String syncId, float dx) {
        if (selection == null) {
            return;
        }
        selection.selectFromSwipeDelta(syncId, dx);
        updateSelectedRows();
    }

    private void updateSelectedRows() {
        for (ConflictRowViews row: conflictRows) {
            ConflictChoice selectedChoice = selection == null
                    ? null
                    : selection.getSelectedChoice(row.syncId);
            row.localColumn.setSelected(ConflictChoice.LOCAL.equals(selectedChoice));
            row.remoteColumn.setSelected(ConflictChoice.REMOTE.equals(selectedChoice));
        }
        if (confirmButton != null) {
            confirmButton.setEnabled(selection != null && selection.isComplete());
        }
    }

    private String sideDetails(Activity activity, SyncConflictDisplayModel.Side side) {
        StringBuilder details = new StringBuilder();
        details.append(activity.getString(R.string.sync_conflict_status_label))
                .append(": ").append(displayValue(side.getStatus())).append('\n');
        details.append(activity.getString(R.string.sync_conflict_deleted_label))
                .append(": ").append(displayValue(side.getDeleted())).append('\n');
        details.append(activity.getString(R.string.sync_conflict_modified_at_label))
                .append(": ").append(displayValue(side.getModifiedAt())).append('\n');
        details.append(activity.getString(R.string.sync_conflict_modified_by_label))
                .append(": ").append(displayValue(side.getModifiedByName()));
        return details.toString();
    }

    private String displayValue(Object value) {
        return value == null ? "?" : value.toString();
    }

    private void resolveSelections(final List<ConflictListSelection.SelectedChoice> choices) {
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final AndroidWebDavSyncRunner runner = new AndroidWebDavSyncRunner(activity);
        try {
            runner.chooseAll(choices);
        } catch (RuntimeException e) {
            if (confirmButton != null) {
                confirmButton.setEnabled(selection != null && selection.isComplete());
            }
            Toast.makeText(activity, displayExceptionMessage(e), Toast.LENGTH_LONG).show();
            return;
        }

        if (confirmButton != null) {
            confirmButton.setEnabled(false);
        }
        dismissAllowingStateLoss();
        Toast.makeText(activity, R.string.sync_started, Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final WebDavSyncResult result;
                try {
                    result = runner.synchronizeOrResume();
                } catch (RuntimeException e) {
                    final WebDavSyncResult failed = WebDavSyncResult.status(
                            WebDavSyncResult.Status.CONFIGURATION_ERROR,
                            "Resolved conflict synchronization failed before it could start");
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onResolvedConflictUploadResult(activity, failed);
                        }
                    });
                    return;
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        onResolvedConflictUploadResult(activity, result);
                    }
                });
            }
        }, "sholi-resolved-conflict-sync").start();
    }

    private void onResolvedConflictUploadResult(final Activity activity, final WebDavSyncResult result) {
        if (result.getStatus() == WebDavSyncResult.Status.CONFLICTS) {
            Toast.makeText(activity, R.string.sync_conflicts_found, Toast.LENGTH_LONG).show();
            activity.getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    showNext(activity.getFragmentManager(), conflictMessage(activity, result));
                }
            });
            return;
        }
        if (result.isSuccess()) {
            refreshVisibleList(activity);
        }
        Toast.makeText(activity, result.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void refreshVisibleList(Activity activity) {
        if (activity == null) {
            return;
        }
        Fragment fragment = activity.getFragmentManager().findFragmentById(R.id.container);
        if (fragment instanceof AbstractListFragment
                && fragment.isAdded()
                && fragment.getActivity() == activity) {
            ((AbstractListFragment) fragment).refreshList();
        }
    }

    private String conflictMessage(Activity activity, WebDavSyncResult result) {
        String message = result.getMessage();
        if (message != null && message.trim().length() > 0) {
            return message;
        }
        return activity.getString(R.string.sync_conflicts_found);
    }

    private String displayExceptionMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null ? "Could not save conflict selection" : message;
    }

    private static final class ConflictRowViews {
        private final String syncId;
        private final View localColumn;
        private final View remoteColumn;

        ConflictRowViews(String syncId, View localColumn, View remoteColumn) {
            this.syncId = syncId;
            this.localColumn = localColumn;
            this.remoteColumn = remoteColumn;
        }
    }
}
