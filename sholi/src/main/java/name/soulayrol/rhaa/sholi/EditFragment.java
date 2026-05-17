/*
 * ShoLi, a simple tool to produce short lists.
 * Copyright (C) 2013,2014,2015  David Soulayrol
 *
 * ShoLi is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ShoLi is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package name.soulayrol.rhaa.sholi;

import android.app.ActionBar;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import de.greenrobot.dao.query.LazyList;
import de.greenrobot.dao.query.QueryBuilder;
import name.soulayrol.rhaa.sholi.data.Operations;
import name.soulayrol.rhaa.sholi.data.model.Checkable;
import name.soulayrol.rhaa.sholi.data.model.Item;
import name.soulayrol.rhaa.sholi.data.model.ItemDao;
import name.soulayrol.rhaa.sholi.sync.items.ItemAddResolution;
import name.soulayrol.rhaa.sholi.sync.items.ItemSyncMetadata;


public class EditFragment extends AbstractListFragment {

    private static final String BUNDLE_KEY_FILTER = "filter";

    private Button _newItemButton;

    private TextView _newItemEdit;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_edit, container, false);

        _newItemButton = (Button) view.findViewById(R.id.list_btn);
        _newItemEdit = (EditText) view.findViewById(R.id.list_edit);

        _newItemEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                getAdapter().setLazyList(createList(getActivity()));
            }
        });

        _newItemButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (addItem(_newItemEdit.getText().toString().trim()) != 0)
                    _newItemEdit.setText("");
                getAdapter().setLazyList(createList(getActivity()));
            }
        });

        // It is too early to call getListView here, so we fetch the view from its ID.
        ListView listView = (ListView) view.findViewById(android.R.id.list);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new SelectionModeHandler());

        ActionBar bar = getActivity().getActionBar();
        bar.setDisplayHomeAsUpEnabled(true);
        bar.setSubtitle(R.string.fragment_edit_title);

        return view;
    }

    @Override
    public void onDestroy() {
        getActivity().getActionBar().setSubtitle(null);
        super.onDestroy();
    }

    @Override
    protected LazyList<Item> createList(Context context) {
        QueryBuilder builder = getSession().getItemDao().queryBuilder();
        String constraint = null;
        boolean doShow = false;
        LazyList<Item> list;

        // First build the list to be displayed with loose search.
        builder.where(ItemDao.Properties.Deleted.eq(false));
        if (_newItemEdit != null) {
            constraint = _newItemEdit.getEditableText().toString().trim();
            if (constraint != null && !constraint.isEmpty())
                builder.where(ItemDao.Properties.Name.like('%' + constraint + '%'));
        }
        list = builder.orderAsc(ItemDao.Properties.Name).listLazy();

        // Then check exact equality if necessary. Eventually make a new search.
        if (constraint != null && !constraint.isEmpty()) {
            if (list.isEmpty())
                doShow = true;
            if (list.size() == 1)
                doShow = !list.get(0).getName().equals(constraint);
            else if (list.size() > 1) {
                builder = getSession().getItemDao().queryBuilder();
                doShow = builder.where(
                        ItemDao.Properties.Name.eq(constraint),
                        ItemDao.Properties.Deleted.eq(false))
                        .buildCount().count() == 0;
            }
        }

        if (_newItemButton != null) {
            int visibility = _newItemButton.getVisibility();
            // Only call setVisibility when necessary.
            if (visibility == View.GONE && doShow)
                _newItemButton.setVisibility(View.VISIBLE);
            else if (visibility == View.VISIBLE && !doShow)
                _newItemButton.setVisibility(View.GONE);
        }
        return list;
    }

    @Override
    protected void updateItem(Item item) {
        switch (item.getStatus()) {
            case Checkable.OFF_LIST:
                item.setStatus(Checkable.UNCHECKED);
                break;
            case Checkable.UNCHECKED:
            case Checkable.CHECKED:
                item.setStatus(Checkable.OFF_LIST);
                break;
        }

        Operations.touch(getActivity(), item);
        getSession().getItemDao().update(item);
        getAdapter().notifyDataSetChanged();
    }

    private long addItem(String name) {
        String syncId = ItemSyncMetadata.initialSyncIdForName(name);
        Item existing = findItemBySyncId(syncId);
        ItemAddResolution resolution = ItemAddResolution.resolve(syncId, existing);
        switch (resolution.getAction()) {
            case RESTORE_TOMBSTONE:
                ItemAddResolution.restoreTombstone(
                        existing,
                        Checkable.UNCHECKED,
                        System.currentTimeMillis(),
                        Operations.modifiedByName(getActivity()));
                getSession().getItemDao().update(existing);
                return existing.getId();
            case IGNORE_ACTIVE_DUPLICATE:
                return 0;
            case INSERT_NEW:
                Item item = Operations.newItem(getActivity(), name, Checkable.UNCHECKED);
                item.setSyncId(resolution.getSyncId());
                return getSession().getItemDao().insert(item);
            default:
                throw new IllegalStateException("Unsupported add action: " + resolution.getAction());
        }
    }

    private Item findItemBySyncId(String syncId) {
        return getSession().getItemDao().queryBuilder()
                .where(ItemDao.Properties.SyncId.eq(syncId))
                .unique();
    }

    private class SelectionModeHandler implements ListView.MultiChoiceModeListener {

        @Override
        public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
            int count = getListView().getCheckedItemCount();
            switch (count) {
                case 0:
                    mode.setSubtitle(null);
                    break;
                default:
                    mode.setSubtitle(getResources().getQuantityString(
                            R.plurals.selectedItems, count, count));
                    break;
            }
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = getActivity().getMenuInflater();
            inflater.inflate(R.menu.list_select, menu);
            mode.setTitle(R.string.fragment_edit_selection_mode_title);
            _newItemEdit.setVisibility(View.INVISIBLE);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
            case R.id.action_erase:
                getSession().runInTx(new Runnable() {
                    @Override
                    public void run() {
                        for (long id : getListView().getCheckedItemIds())
                            markItemDeleted(id);
                    }
                });
                getAdapter().setLazyList(createList(getActivity()));
                break;
            }
            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            _newItemEdit.setVisibility(View.VISIBLE);
        }

        private void markItemDeleted(long id) {
            Item item = getSession().getItemDao().load(id);
            if (item != null && !Boolean.TRUE.equals(item.getDeleted())) {
                Operations.markDeleted(getActivity(), item);
                getSession().getItemDao().update(item);
            }
        }
    }
}
