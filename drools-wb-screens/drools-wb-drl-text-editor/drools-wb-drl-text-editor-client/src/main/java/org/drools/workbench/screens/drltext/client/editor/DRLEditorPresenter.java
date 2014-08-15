/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.drltext.client.editor;

import static org.uberfire.client.common.ConcurrentChangePopup.newConcurrentDelete;
import static org.uberfire.client.common.ConcurrentChangePopup.newConcurrentRename;
import static org.uberfire.client.common.ConcurrentChangePopup.newConcurrentUpdate;

import java.util.List;

import javax.annotation.PostConstruct;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.New;
import javax.inject.Inject;

import org.drools.workbench.models.datamodel.rule.DSLSentence;
import org.drools.workbench.screens.drltext.client.resources.i18n.DRLTextEditorConstants;
import org.drools.workbench.screens.drltext.client.type.DRLResourceType;
import org.drools.workbench.screens.drltext.client.type.DSLRResourceType;
import org.drools.workbench.screens.drltext.model.DrlModelContent;
import org.drools.workbench.screens.drltext.service.DRLTextEditorService;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.guvnor.common.services.shared.version.events.RestoreEvent;
import org.jboss.errai.common.client.api.Caller;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.kie.workbench.common.widgets.client.callbacks.CommandBuilder;
import org.kie.workbench.common.widgets.client.callbacks.CommandDrivenErrorCallback;
import org.kie.workbench.common.widgets.client.callbacks.DefaultErrorCallback;
import org.kie.workbench.common.widgets.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.kie.workbench.common.widgets.client.menu.FileMenuBuilder;
import org.kie.workbench.common.widgets.client.popups.file.CommandWithCommitMessage;
import org.kie.workbench.common.widgets.client.popups.file.SaveOperationService;
import org.kie.workbench.common.widgets.client.popups.validation.DefaultFileNameValidator;
import org.kie.workbench.common.widgets.client.popups.validation.ValidationPopup;
import org.kie.workbench.common.widgets.client.resources.i18n.CommonConstants;
import org.kie.workbench.common.widgets.metadata.client.callbacks.MetadataSuccessCallback;
import org.kie.workbench.common.widgets.metadata.client.widget.MetadataWidget;
import org.uberfire.backend.vfs.ObservablePath;
import org.uberfire.backend.vfs.Path;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.callbacks.Callback;
import org.uberfire.client.common.MultiPageEditor;
import org.uberfire.client.common.Page;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.client.workbench.events.BeforeClosePlaceEvent;
import org.uberfire.client.workbench.events.ChangeTitleWidgetEvent;
import org.uberfire.lifecycle.IsDirty;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnMayClose;
import org.uberfire.lifecycle.OnSave;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.ParameterizedCommand;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.workbench.events.NotificationEvent;
import org.uberfire.workbench.model.menu.Menus;
import org.uberfire.workbench.type.FileNameUtil;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.user.client.ui.IsWidget;

/**
 * This is the default rule editor widget (just text editor based).
 */
@Dependent
@WorkbenchEditor(identifier = "DRLEditor", supportedTypes = {
		DRLResourceType.class, DSLRResourceType.class })
public class DRLEditorPresenter {

	@Inject
	private Caller<DRLTextEditorService> drlTextEditorService;

	@Inject
	private Caller<MetadataService> metadataService;

	@Inject
	private Event<NotificationEvent> notification;

	@Inject
	private Event<ChangeTitleWidgetEvent> changeTitleNotification;

	@Inject
	private PlaceManager placeManager;

	@Inject
	private DRLEditorView view;

	@Inject
	private MetadataWidget metadataWidget;

	@Inject
	private MultiPageEditor multiPage;

	@Inject
	private DRLResourceType resourceTypeDRL;

	@Inject
	private DSLRResourceType resourceTypeDSLR;

	@Inject
	private DefaultFileNameValidator fileNameValidator;

	@Inject
	@New
	private FileMenuBuilder menuBuilder;
	private Menus menus;

	private ObservablePath path;
	private PlaceRequest place;
	private boolean isReadOnly;
	private boolean isDSLR;
	private String version;
	private ObservablePath.OnConcurrentUpdateEvent concurrentUpdateSessionInfo = null;

	private static final String PATH_RESOURCE = "pathResource";
	private static final String DEFAULT_MASTER = "default://master@";
	private static final String PARAM_READONLY = "readOnly";
	private static final String PARAM_REPOSITORY = "repository";

	@Inject
	private Event<BeforeClosePlaceEvent> closePlaceEvent;

	@PostConstruct
	public void init() {
		view.init(this);
	}

	@OnStartup
	public void onStartup(final ObservablePath path, final PlaceRequest place) {
		// JSNI
		final JavaScriptObject window = getCurrentWindow();
		registerHandlers(DRLEditorPresenter.this, window);

		this.path = path;
		this.place = place;
		this.isReadOnly = place.getParameter("readOnly", null) != null;
		this.version = place.getParameter("version", null);
		this.isDSLR = resourceTypeDSLR.accept(path);

		this.path.onRename(new Command() {
			@Override
			public void execute() {
				changeTitleNotification.fire(new ChangeTitleWidgetEvent(place,
						getTitle(), null));
			}
		});
		this.path
				.onConcurrentUpdate(new ParameterizedCommand<ObservablePath.OnConcurrentUpdateEvent>() {
					@Override
					public void execute(
							final ObservablePath.OnConcurrentUpdateEvent eventInfo) {
						concurrentUpdateSessionInfo = eventInfo;
					}
				});

		this.path
				.onConcurrentRename(new ParameterizedCommand<ObservablePath.OnConcurrentRenameEvent>() {
					@Override
					public void execute(
							final ObservablePath.OnConcurrentRenameEvent info) {
						newConcurrentRename(info.getSource(), info.getTarget(),
								info.getIdentity(), new Command() {
									@Override
									public void execute() {
										disableMenus();
									}
								}, new Command() {
									@Override
									public void execute() {
										reload();
									}
								}).show();
					}
				});

		this.path
				.onConcurrentDelete(new ParameterizedCommand<ObservablePath.OnConcurrentDelete>() {
					@Override
					public void execute(
							final ObservablePath.OnConcurrentDelete info) {
						newConcurrentDelete(info.getPath(), info.getIdentity(),
								new Command() {
									@Override
									public void execute() {
										disableMenus();
									}
								}, new Command() {
									@Override
									public void execute() {
										placeManager.closePlace(place);
									}
								}).show();
					}
				});

		makeMenuBar();

		view.showBusyIndicator(CommonConstants.INSTANCE.Loading());

		loadContent();
	}

	private void reload() {
		concurrentUpdateSessionInfo = null;
		changeTitleNotification.fire(new ChangeTitleWidgetEvent(place,
				getTitle(), null));
		view.showBusyIndicator(CommonConstants.INSTANCE.Loading());
		loadContent();
	}

	private void disableMenus() {
		menus.getItemsMap().get(FileMenuBuilder.MenuItems.COPY)
				.setEnabled(false);
		menus.getItemsMap().get(FileMenuBuilder.MenuItems.RENAME)
				.setEnabled(false);
		menus.getItemsMap().get(FileMenuBuilder.MenuItems.DELETE)
				.setEnabled(false);
		menus.getItemsMap().get(FileMenuBuilder.MenuItems.VALIDATE)
				.setEnabled(false);
	}

	private void loadContent() {
		drlTextEditorService
				.call(getLoadContentSuccessCallback(),
						new CommandDrivenErrorCallback(view,
								new CommandBuilder().addNoSuchFileException(
										view, multiPage, menus).build()))
				.loadContent(path);
	}

	private RemoteCallback<DrlModelContent> getLoadContentSuccessCallback() {
		return new RemoteCallback<DrlModelContent>() {

			@Override
			public void callback(final DrlModelContent content) {
				// Path is set to null when the Editor is closed (which can
				// happen before async calls complete).
				if (path == null) {
					return;
				}

				multiPage.clear();
				multiPage
						.addWidget(view, DRLTextEditorConstants.INSTANCE.DRL());

				multiPage.addPage(new Page(metadataWidget,
						CommonConstants.INSTANCE.MetadataTabTitle()) {
					@Override
					public void onFocus() {
						if (!metadataWidget.isAlreadyLoaded()) {
							metadataWidget
									.showBusyIndicator(CommonConstants.INSTANCE
											.Loading());
							metadataService.call(
									new MetadataSuccessCallback(metadataWidget,
											isReadOnly),
									new HasBusyIndicatorDefaultErrorCallback(
											metadataWidget)).getMetadata(path);
						}
					}

					@Override
					public void onLostFocus() {
						// Nothing to do
					}
				});
				final String drl = assertContent(content.getDrl());
				final List<String> fullyQualifiedClassNames = content
						.getFullyQualifiedClassNames();
				final List<DSLSentence> dslConditions = content
						.getDslConditions();
				final List<DSLSentence> dslActions = content.getDslActions();

				// Populate view
				if (isDSLR) {
					view.setContent(drl, fullyQualifiedClassNames,
							dslConditions, dslActions);
				} else {
					view.setContent(drl, fullyQualifiedClassNames);
				}
				view.hideBusyIndicator();
			}

			private String assertContent(final String drl) {
				if (drl == null || drl.isEmpty()) {
					return "";
				}
				return drl;
			}

		};
	}

	public void loadClassFields(final String fullyQualifiedClassName,
			final Callback<List<String>> callback) {
		drlTextEditorService.call(getLoadClassFieldsSuccessCallback(callback),
				new HasBusyIndicatorDefaultErrorCallback(view))
				.loadClassFields(path, fullyQualifiedClassName);

	}

	private RemoteCallback<List<String>> getLoadClassFieldsSuccessCallback(
			final Callback<List<String>> callback) {
		return new RemoteCallback<List<String>>() {

			@Override
			public void callback(final List<String> fields) {
				callback.callback(fields);
			}
		};
	}

	private void makeMenuBar() {
		if (isReadOnly) {
			menus = menuBuilder.addRestoreVersion(path).build();
		} else {
			menus = menuBuilder.addSave(new Command() {
				@Override
				public void execute() {
					onSave();
				}
			}).addCopy(path, fileNameValidator)
					.addRename(path, fileNameValidator).addDelete(path)
					.addValidate(onValidate()).build();
		}
	}

	private Command onValidate() {
		return new Command() {
			@Override
			public void execute() {
				drlTextEditorService.call(
						new RemoteCallback<List<ValidationMessage>>() {
							@Override
							public void callback(
									final List<ValidationMessage> results) {
								if (results == null || results.isEmpty()) {
									notification.fire(new NotificationEvent(
											CommonConstants.INSTANCE
													.ItemValidatedSuccessfully(),
											NotificationEvent.NotificationType.SUCCESS));
								} else {
									ValidationPopup.showMessages(results);
								}
							}
						}, new DefaultErrorCallback()).validate(path,
						view.getContent());
			}
		};
	}

	@OnSave
	public void onSave() {
		if (isReadOnly) {
			view.alertReadOnly();
			return;
		}

		if (concurrentUpdateSessionInfo != null) {
			newConcurrentUpdate(concurrentUpdateSessionInfo.getPath(),
					concurrentUpdateSessionInfo.getIdentity(), new Command() {
						@Override
						public void execute() {
							save();
						}
					}, new Command() {
						@Override
						public void execute() {
							// cancel?
						}
					}, new Command() {
						@Override
						public void execute() {
							reload();
						}
					}).show();
		} else {
			save();
		}
	}

	private void save() {
		new SaveOperationService().save(path, new CommandWithCommitMessage() {
			@Override
			public void execute(final String commitMessage) {
				view.showBusyIndicator(CommonConstants.INSTANCE.Saving());
				drlTextEditorService.call(getSaveSuccessCallback(),
						new HasBusyIndicatorDefaultErrorCallback(view)).save(
						path, view.getContent(), metadataWidget.getContent(),
						commitMessage);
			}
		});
		concurrentUpdateSessionInfo = null;
	}

	private RemoteCallback<Path> getSaveSuccessCallback() {
		return new RemoteCallback<Path>() {

			@Override
			public void callback(final Path path) {
				view.setNotDirty();
				view.hideBusyIndicator();
				metadataWidget.resetDirty();
				notification.fire(new NotificationEvent(
						CommonConstants.INSTANCE.ItemSavedSuccessfully()));
			}
		};
	}

	@IsDirty
	public boolean isDirty() {
		return view.isDirty() || metadataWidget.isDirty();
	}

	@OnClose
	public void onClose() {
		this.path = null;
	}

	@OnMayClose
	public boolean checkIfDirty() {
		if (isDirty()) {
			return view.confirmClose();
		}
		return true;
	}

	@WorkbenchPartTitle
	public String getTitle() {
		String title = "";
		String fileName = "";
		if (resourceTypeDRL.accept(path)) {
			title = DRLTextEditorConstants.INSTANCE.drlEditorTitle();
			fileName = FileNameUtil.removeExtension(path, resourceTypeDRL);
		} else if (resourceTypeDSLR.accept(path)) {
			title = DRLTextEditorConstants.INSTANCE.dslrEditorTitle();
			fileName = FileNameUtil.removeExtension(path, resourceTypeDSLR);
		}

		if (version != null) {
			fileName = fileName + " v" + version;
		}
		return (fileName == null) ? "" : title + " [" + fileName + "]";
	}

	@WorkbenchPartView
	public IsWidget getWidget() {
		return multiPage;
	}

	@WorkbenchMenu
	public Menus getMenus() {
		return menus;
	}

	public void onRestore(@Observes RestoreEvent restore) {
		if (path == null || restore == null || restore.getPath() == null) {
			return;
		}
		if (path.equals(restore.getPath())) {
			loadContent();
			notification.fire(new NotificationEvent(CommonConstants.INSTANCE
					.ItemRestored()));
		}
	}

	public void close() {
		closePlaceEvent.fire(new BeforeClosePlaceEvent(this.place, true));
	}

	

	public static native void redirect(String url)/*-{
													$wnd.location = url;
													}-*/;
	
	//JSNI
	native JavaScriptObject openWindow(String url) /*-{
		return $wnd.open(url, 'blank');
	}-*/;

	native JavaScriptObject getCurrentWindow() /*-{
		return $wnd;
	}-*/;

	native JavaScriptObject registerHandlers(DRLEditorPresenter jsni,
		JavaScriptObject window) /*-{
		window.onbeforeunload = doOnbeforeunload;
		function doOnbeforeunload() {
			jsni.@org.drools.workbench.screens.drltext.client.editor.DRLEditorPresenter::onWindowClosed()();
		}
	}-*/;

	private void onWindowClosed() {
		close();
	}

}
