/*
 * Created on 2 juil. 2003
 *
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package com.biglybt.ui.swt.views;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.biglybt.pif.ui.UIInstance;
import com.biglybt.pif.ui.UIManager;
import com.biglybt.pif.ui.UIManagerListener;
import com.biglybt.pifimpl.local.PluginInitializer;
import com.biglybt.ui.common.ToolBarItem;
import com.biglybt.ui.common.table.*;
import com.biglybt.ui.common.table.impl.TableColumnManager;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerTPSListener;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.tracker.TrackerPeerSource;
import com.biglybt.core.tracker.client.TRTrackerAnnouncer;
import com.biglybt.core.util.AERunnable;
import com.biglybt.core.util.Debug;
import com.biglybt.core.util.TorrentUtils;
import com.biglybt.pif.ui.UIPluginViewToolBarListener;
import com.biglybt.pif.ui.config.Parameter;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.pif.ui.toolbar.UIToolBarItem;
import com.biglybt.ui.swt.Messages;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.maketorrent.MultiTrackerEditor;
import com.biglybt.ui.swt.maketorrent.TrackerEditorListener;
import com.biglybt.ui.swt.pif.UISWTInstance;
import com.biglybt.ui.swt.pif.UISWTViewEvent;
import com.biglybt.ui.swt.pifimpl.UISWTViewEventImpl;
import com.biglybt.ui.swt.shells.MessageBoxShell;
import com.biglybt.ui.swt.views.table.TableSelectedRowsListener;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.table.TableViewSWTMenuFillListener;
import com.biglybt.ui.swt.views.table.impl.TableViewFactory;
import com.biglybt.ui.swt.views.table.impl.TableViewSWT_TabsCommon;
import com.biglybt.ui.swt.views.table.impl.TableViewTab;
import com.biglybt.ui.swt.views.tableitems.tracker.*;

import com.biglybt.ui.selectedcontent.SelectedContent;
import com.biglybt.ui.selectedcontent.SelectedContentManager;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;


/**
 * aka "Sources" view
 */
public class TrackerView
	extends TableViewTab<TrackerPeerSource>
	implements TableLifeCycleListener, TableDataSourceChangedListener,
	DownloadManagerTPSListener, TableViewSWTMenuFillListener,
	TableSelectionListener, UIPluginViewToolBarListener
{
	private static boolean registeredCoreSubViews = false;

	private final static TableColumnCore[] basicItems = {
		new TypeItem(TableManager.TABLE_TORRENT_TRACKERS),
		new NameItem(TableManager.TABLE_TORRENT_TRACKERS),
		new StatusItem(TableManager.TABLE_TORRENT_TRACKERS),
		new PeersItem(TableManager.TABLE_TORRENT_TRACKERS),
		new SeedsItem(TableManager.TABLE_TORRENT_TRACKERS),
		new LeechersItem(TableManager.TABLE_TORRENT_TRACKERS),
		new CompletedItem(TableManager.TABLE_TORRENT_TRACKERS),
		new UpdateInItem(TableManager.TABLE_TORRENT_TRACKERS),
		new IntervalItem(TableManager.TABLE_TORRENT_TRACKERS),
		new LastUpdateItem(TableManager.TABLE_TORRENT_TRACKERS),
	};

	static{
		TableColumnManager tcManager = TableColumnManager.getInstance();

		tcManager.setDefaultColumnNames( TableManager.TABLE_TORRENT_TRACKERS, basicItems );
	}
	
	public static final String MSGID_PREFIX = "TrackerView";

	private DownloadManager 	manager;
	private boolean				enable_tabs = true;

	private TableViewSWT<TrackerPeerSource> tv;

	/**
	 * Initialize
	 *
	 */
	public TrackerView() {
		super(MSGID_PREFIX);
	}

	@Override
	public TableViewSWT<TrackerPeerSource>
	initYourTableView()
	{
		tv = TableViewFactory.createTableViewSWT(
				TrackerPeerSource.class,
				TableManager.TABLE_TORRENT_TRACKERS,
				getPropertiesPrefix(),
				basicItems,
				basicItems[0].getName(),
				SWT.MULTI | SWT.FULL_SELECTION | SWT.VIRTUAL );

		tv.addLifeCycleListener(this);
		tv.addMenuFillListener(this);
		tv.addTableDataSourceChangedListener(this, true);
		tv.addSelectionListener(this, false);

		tv.addKeyListener(
			new KeyAdapter(){
				@Override
				public void
				keyPressed(
					KeyEvent e )
				{
					if ( e.stateMask == 0 && e.keyCode == SWT.DEL ){

						removeTrackerPeerSources(tv.getSelectedDataSources().toArray());
						
						e.doit = false;
					}
				}});
		
								
		tv.setEnableTabViews(enable_tabs,true,null);

		UIFunctionsSWT uiFunctions = UIFunctionsManagerSWT.getUIFunctionsSWT();
		if (uiFunctions != null) {
			UISWTInstance pluginUI = uiFunctions.getUISWTInstance();

			registerPluginViews(pluginUI);
		}

		return tv;
	}

	private static void removeTrackerPeerSources( Object[] datasources) {
		List<TrackerPeerSource> list = new ArrayList<>();
		for (Object o : datasources) {
			if ((o instanceof TrackerPeerSource)
					&& ((TrackerPeerSource) o).canDelete()) {
				list.add((TrackerPeerSource) o);
			}
		}
		removeTrackerPeerSources(list);
	}

	private static void removeTrackerPeerSources(List<TrackerPeerSource> list) {
		int numLeft = list.size();
		if (numLeft == 0) {
			return;
		}

		TrackerPeerSource toRemove = list.get(0);
		if (toRemove == null) {
			return;
		}

		MessageBoxShell mb = new MessageBoxShell(
				MessageText.getString("message.confirm.delete.title"),
				MessageText.getString("message.confirm.delete.text", new String[] {
					toRemove.getName()
				}), new String[] {
					MessageText.getString("Button.yes"),
					MessageText.getString("Button.no")
				}, 1);

		if (numLeft > 1) {
			String sDeleteAll = MessageText.getString("v3.deleteContent.applyToAll",
					new String[] {
						"" + numLeft
					});
			mb.addCheckBox("!" + sDeleteAll + "!", Parameter.MODE_BEGINNER, false);
		}

		mb.setRememberOnlyIfButton(0);
		mb.setRemember("removeTracker", false,
				MessageText.getString("MessageBoxWindow.nomoreprompting"));

		mb.open(result -> {
			if (result == -1) {
				// cancel
				return;
			}
			boolean remove = result == 0;
			boolean doAll = mb.getCheckBoxEnabled();
			if (doAll) {
				if (remove) {
					for (TrackerPeerSource tps : list) {
						if (tps.canDelete()) {
							tps.delete();
						}
					}
				}
			} else {
				if (remove) {
					toRemove.delete();
				}
				// Loop with remaining tags to be removed
				list.remove(0);
				removeTrackerPeerSources(list);
			}
		});
	}

	private void registerPluginViews(final UISWTInstance pluginUI) {
		if (pluginUI == null || registeredCoreSubViews) {
			return;
		}

		pluginUI.addView(TableManager.TABLE_TORRENT_TRACKERS, "ScrapeInfoView",
				ScrapeInfoView.class, manager);

		registeredCoreSubViews = true;

		final UIManager uiManager = PluginInitializer.getDefaultInterface().getUIManager();
		uiManager.addUIListener(new UIManagerListener() {

			@Override
			public void UIAttached(UIInstance instance) {

			}

			@Override
			public void UIDetached(UIInstance instance) {
				if (!(instance instanceof UISWTInstance)) {
					return;
				}

				registeredCoreSubViews = false;
				pluginUI.removeViews(TableManager.TABLE_TORRENT_TRACKERS, "ScrapeInfoView");
				uiManager.removeUIListener(this);
			}
		});
	}


	@Override
	public void
	fillMenu(
		String sColumnName, Menu menu)
	{
		if (tv == null) {
			return;
		}
		final Object[] sources = tv.getSelectedDataSources().toArray();

		boolean	found_tracker		= false;
		boolean	found_dht_tracker	= false;
		boolean	update_ok 			= false;
		boolean delete_ok			= false;

		for ( Object o: sources ){

			TrackerPeerSource ps = (TrackerPeerSource)o;

			if ( ps.getType() == TrackerPeerSource.TP_TRACKER ){

				found_tracker = true;

			}

			if ( ps.getType() == TrackerPeerSource.TP_DHT  ){

				found_dht_tracker = true;
			}

			int	state = ps.getStatus();

			if ( 	( 	state == TrackerPeerSource.ST_ONLINE ||
						state == TrackerPeerSource.ST_QUEUED ||
						state == TrackerPeerSource.ST_ERROR ) &&
					!ps.isUpdating() &&
					ps.canManuallyUpdate()){

				update_ok = true;
			}

			if ( ps.canDelete()){

				delete_ok = true;
			}
		}

		boolean	needs_sep = false;

		if ( found_tracker || found_dht_tracker ){

			final MenuItem update_item = new MenuItem( menu, SWT.PUSH);

			Messages.setLanguageText(update_item, "GeneralView.label.trackerurlupdate");

			update_item.setEnabled( update_ok );

			update_item.addListener(
				SWT.Selection,
				new TableSelectedRowsListener(tv)
				{
					@Override
					public void
					run(
						TableRowCore row )
					{
						for ( Object o: sources ){

							TrackerPeerSource ps = (TrackerPeerSource)o;

							if ( ps.canManuallyUpdate()){

								ps.manualUpdate();
							}
						}
					}
				});

			if ( found_tracker ){

				final MenuItem edit_item = new MenuItem( menu, SWT.PUSH);

				Messages.setLanguageText(edit_item, "MyTorrentsView.menu.editTracker" );

				edit_item.addListener(
					SWT.Selection,
					new TableSelectedRowsListener(tv)
					{
						@Override
						public boolean
						run(
							TableRowCore[] rows )
						{
							final TOTorrent torrent = manager.getTorrent();

							if (torrent != null) {

								Utils.execSWTThread(
									new Runnable()
									{
										@Override
										public void
										run()
										{
											List<List<String>> group = TorrentUtils.announceGroupsToList(torrent);

											new MultiTrackerEditor(null,null, group, new TrackerEditorListener() {
												@Override
												public void trackersChanged(String str, String str2, List<List<String>> _group) {
													TorrentUtils.listToAnnounceGroups(_group, torrent);

													try {
														TorrentUtils.writeToFile(torrent);
													} catch (Throwable e2) {

														Debug.printStackTrace(e2);
													}

													TRTrackerAnnouncer tc = manager.getTrackerClient();

													if (tc != null) {

														tc.resetTrackerUrl(true);
													}
												}
											}, true, true );
										}
									});

							}

							return( true );
						}
					});

				TOTorrent torrent = manager.getTorrent();

				edit_item.setEnabled( torrent != null && !TorrentUtils.isReallyPrivate( torrent ));
			}

			needs_sep = true;
		}

		if ( delete_ok ){

			final MenuItem delete_item = new MenuItem( menu, SWT.PUSH);

			Messages.setLanguageText(delete_item, "Button.remove" );
			Utils.setMenuItemImage(delete_item, "delete");

			delete_item.addListener(SWT.Selection, event -> removeTrackerPeerSources(sources));

			needs_sep = true;
		}

		if ( needs_sep ){

			new MenuItem( menu, SWT.SEPARATOR );
		}
	}

	@Override
	public void
	addThisColumnSubMenu(
		String columnName,
		Menu menuThisColumn)
	{
	}

	@Override
	public void
	trackerPeerSourcesChanged()
	{
		Utils.execSWTThread(
			new AERunnable()
			{
				@Override
				public void
				runSupport()
				{
					if ( manager == null || tv.isDisposed()){

						return;
					}

					tv.removeAllTableRows();

					addExistingDatasources();
				}
			});
	}

	@Override
	public void
	tableDataSourceChanged(
		Object newDataSource )
 {
		DownloadManager newManager = ViewUtils.getDownloadManagerFromDataSource( newDataSource, manager );

		if (newManager == manager) {
			tv.setEnabled(manager != null);
			return;
		}

		if (manager != null) {
			manager.removeTPSListener(this);
		}

		manager = newManager;

		if (tv.isDisposed()) {
			return;
		}

		tv.removeAllTableRows();
		tv.setEnabled(manager != null);

		if (manager != null) {
			manager.addTPSListener(this);
			addExistingDatasources();
		}
	}

	@Override
	public void tableLifeCycleEventOccurred(TableView tv, int eventType, Map<String, Object> data) {
		switch (eventType) {
			case EVENT_TABLELIFECYCLE_INITIALIZED:
				tableViewInitialized();
				break;
			case EVENT_TABLELIFECYCLE_DESTROYED:
				tableViewDestroyed();
				break;
		}
	}

	public void
	tableViewInitialized()
	{
		if ( manager != null ){

			manager.addTPSListener( this );

			addExistingDatasources();

				// For this view the tab datasource isn't driven by table row selection so we
				// need to update it with the primary data source

	 		TableViewSWT_TabsCommon tabs = tv.getTabsCommon();

	  		if ( tabs != null ){

	  			tabs.triggerTabViewsDataSourceChanged( tv );
	  		}
		}
    }

	public void
	tableViewDestroyed()
	{
		if ( manager != null ){

			manager.removeTPSListener( this );
		}
	}

	private void
	addExistingDatasources()
	{
		if ( manager == null || tv.isDisposed()){

			return;
		}

		List<TrackerPeerSource> tps = manager.getTrackerPeerSources();

		tv.addDataSources( tps.toArray( (new TrackerPeerSource[tps.size()])));

		tv.processDataSourceQueueSync();
	}

	protected void
	updateSelectedContent()
	{
		if ( tv == null ){
			
			return;
		}
		
		Object[] dataSources = tv.getSelectedDataSources(true);

		if ( dataSources.length == 0 ){

	      	String id = "DMDetails_Sources";
	      	if (manager != null) {
	      		if (manager.getTorrent() != null) {
	  					id += "." + manager.getInternalName();
	      		} else {
	      			id += ":" + manager.getSize();
	      		}
						SelectedContentManager.changeCurrentlySelectedContent(id,
								new SelectedContent[] {
									new SelectedContent(manager)
						});
					} else {
						SelectedContentManager.changeCurrentlySelectedContent(id, null);
					}
		}else{
			
			SelectedContent[] sc = new SelectedContent[dataSources.length];
			
			for ( int i=0;i<sc.length;i++){
				
				sc[i] = new SelectedContent();
			}
			
			SelectedContentManager.changeCurrentlySelectedContent(tv.getTableID(),
					sc, tv);
		}

	}
	
	@Override
	public void deselected(TableRowCore[] rows) {
		updateSelectedContent();
	}

	@Override
	public void focusChanged(TableRowCore focus) {
	}

	@Override
	public void selected(TableRowCore[] rows) {
		updateSelectedContent();
	}
	
	@Override
	public void mouseEnter(TableRowCore row){
	}

	@Override
	public void mouseExit(TableRowCore row){
	}
	
	@Override
	public boolean eventOccurred(UISWTViewEvent event) {
	    switch (event.getType()) {

	      case UISWTViewEvent.TYPE_CREATE:{
	    	  if ( event instanceof UISWTViewEventImpl ){

	    		  String parent = ((UISWTViewEventImpl)event).getParentID();

	    		  enable_tabs = parent != null && parent.equals( UISWTInstance.VIEW_TORRENT_DETAILS );
	    	  }
	    	  break;
	      }
	      case UISWTViewEvent.TYPE_FOCUSGAINED:
	    	  
	    	updateSelectedContent();
	    	
	      	break;

	      case UISWTViewEvent.TYPE_FOCUSLOST:
	    		SelectedContentManager.clearCurrentlySelectedContent();
	    		break;
	    }

	    return( super.eventOccurred(event));
	}

	@Override
	public boolean
	isActive()
	{
		if (tv == null || !tv.isVisible()) {
			return( false );
		}

		return( !tv.getSelectedDataSources().isEmpty());
	}
	
	@Override
	public void refreshToolBarItems(Map<String, Long> list) {
		if (tv == null || !tv.isVisible()) {
			return;
		}

		boolean canEnable = false;
		Object[] datasources = tv.getSelectedDataSources().toArray();

		for (Object object : datasources) {
			if (object instanceof TrackerPeerSource) {
				TrackerPeerSource tps = (TrackerPeerSource) object;
				if (tps.canDelete()) {
					canEnable = true;
					break;
				}
			}
		}

		list.put("remove", canEnable ? UIToolBarItem.STATE_ENABLED : 0);
	}

	@Override
	public boolean toolBarItemActivated(ToolBarItem item, long activationType,
			Object datasource) {
		if (tv == null || !tv.isVisible()) {
			return false;
		}

		if ("remove".equals(item.getID())) {
			removeTrackerPeerSources(tv.getSelectedDataSources().toArray());
			return true;
		}
		return false;
	}
}
