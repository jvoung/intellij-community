/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.profile.codeInspection.ui.header;

import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionProfileModifiableModel;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.profile.codeInspection.BaseInspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.profile.codeInspection.ui.ErrorsConfigurable;
import com.intellij.profile.codeInspection.ui.SingleInspectionProfilePanel;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.JBInsets;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public abstract class InspectionToolsConfigurable extends BaseConfigurable
  implements ErrorsConfigurable, SearchableConfigurable, Configurable.NoScroll {
  public static final String ID = "Errors";
  public static final String DISPLAY_NAME = "Inspections";

  protected final BaseInspectionProfileManager myApplicationProfileManager;
  protected final ProjectInspectionProfileManager myProjectProfileManager;
  private JPanel myProfilePanelHolder;
  private Alarm mySelectionAlarm;
  private InspectionProfileSchemesPanel myAbstractSchemesPanel;

  public InspectionToolsConfigurable(@NotNull ProjectInspectionProfileManager projectProfileManager) {
    myProjectProfileManager = projectProfileManager;
    myApplicationProfileManager = (BaseInspectionProfileManager)InspectionProfileManager.getInstance();
  }

  private Project getProject() {
    return myProjectProfileManager.getProject();
  }

  protected boolean setActiveProfileAsDefaultOnApply() {
    return true;
  }

  @Override
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public String getHelpTopic() {
    return "preferences.inspections";
  }

  @Override
  @NotNull
  public String getId() {
    return ID;
  }

  @Override
  public Runnable enableSearch(final String option) {
    return () -> {
      SingleInspectionProfilePanel panel = getSelectedPanel();
      if (panel != null) {
        showProfile(getSelectedObject());
        panel.setFilter(option);
      }
    };
  }

  @Override
  public JComponent createComponent() {
    final JPanel wholePanel = new JPanel();
    wholePanel.setLayout(new BorderLayout());

    myProfilePanelHolder = new JPanel() {
      @Override
      public void doLayout() {
        Rectangle bounds = new Rectangle(getWidth(), getHeight());
        JBInsets.removeFrom(bounds, getInsets());
        for (Component component : getComponents()) {
          component.setBounds(bounds);
        }
      }

      @Override
      public Dimension getPreferredSize() {
        for (Component component : getComponents()) {
          if (component.isVisible()) {
            return component.getPreferredSize();
          }
        }
        return super.getPreferredSize();
      }

      @Override
      public Dimension getMinimumSize() {
        for (Component component : getComponents()) {
          if (component.isVisible()) {
            return component.getMinimumSize();
          }
        }
        return super.getMinimumSize();
      }
    };
    wholePanel.add(myProfilePanelHolder, BorderLayout.CENTER);

    JPanel profilesHolder = new JPanel();
    profilesHolder.setLayout(new CardLayout());
    myAbstractSchemesPanel = new InspectionProfileSchemesPanel(getProject(),
                                                               myApplicationProfileManager,
                                                               myProjectProfileManager,
                                                               this);
    wholePanel.add(myAbstractSchemesPanel, BorderLayout.NORTH);
    return wholePanel;
  }

  public static InspectionProfileImpl importInspectionProfile(@NotNull Element rootElement,
                                                              @NotNull BaseInspectionProfileManager profileManager,
                                                              @NotNull Project project) {
    InspectionProfileImpl profile =
      new InspectionProfileImpl("TempProfile", InspectionToolRegistrar.getInstance(), profileManager);
    if (Comparing.strEqual(rootElement.getName(), "component")) {
      //import right from .idea/inspectProfiles/xxx.xml
      rootElement = rootElement.getChildren().get(0);
    }
    final Set<String> levels = new HashSet<>();
    for (Element inspectElement : rootElement.getChildren("inspection_tool")) {
      addLevelIfNotNull(levels, inspectElement);
      for (Element s : inspectElement.getChildren("scope")) {
        addLevelIfNotNull(levels, s);
      }
    }
    for (Iterator<String> iterator = levels.iterator(); iterator.hasNext(); ) {
      String level = iterator.next();
      if (profileManager.getOwnSeverityRegistrar().getSeverity(level) != null) {
        iterator.remove();
      }
    }
    if (!levels.isEmpty()) {
      if (!ApplicationManager.getApplication().isUnitTestMode()) {
        if (Messages.showYesNoDialog(project, "Undefined severities detected: " +
                                                  StringUtil.join(levels, ", ") +
                                                  ". Do you want to create them?", "Warning", Messages.getWarningIcon()) ==
            Messages.YES) {
          for (String level : levels) {
            final TextAttributes textAttributes = CodeInsightColors.WARNINGS_ATTRIBUTES.getDefaultAttributes();
            HighlightInfoType.HighlightInfoTypeImpl info =
              new HighlightInfoType.HighlightInfoTypeImpl(new HighlightSeverity(level, 50),
                                                          TextAttributesKey
                                                            .createTextAttributesKey(level));
            profileManager.getOwnSeverityRegistrar()
              .registerSeverity(new SeverityRegistrar.SeverityBasedTextAttributes(textAttributes.clone(), info),
                                textAttributes.getErrorStripeColor());
          }
        }
      } else {
        throw new AssertionError("All of levels must exist in unit-test mode, but actual not exist levels = " + levels);
      }
    }
    profile.readExternal(rootElement);
    profile.setProjectLevel(false);
    profile.initInspectionTools(project);
    return profile;
  }

  private static void addLevelIfNotNull(Set<String> levels, Element inspectElement) {
    final String level = inspectElement.getAttributeValue("level");
    if (level != null) {
      levels.add(level);
    }
  }

  protected abstract InspectionProfileImpl getCurrentProfile();

  @Override
  public boolean isModified() {
    final InspectionProfileImpl selectedProfile = getSelectedObject();
    final InspectionProfileImpl currentProfile = getCurrentProfile();
    if (!Comparing.equal(selectedProfile, currentProfile)) {
      return true;
    }
    final InspectionProfileSchemesModel model = myAbstractSchemesPanel.getModel();
    for (SingleInspectionProfilePanel panel : model.getProfilePanels()) {
      if (panel.isModified()) return true;
    }
    return model.hasDeletedProfiles() ||
           InspectionProfileSchemesModel.getSortedProfiles(myApplicationProfileManager, myProjectProfileManager).size() != model.getSize();
  }

  @Override
  public void apply() {
    myAbstractSchemesPanel.apply();
  }

  protected abstract void applyRootProfile(@NotNull String name, boolean isProjectLevel);

  protected boolean acceptTool(InspectionToolWrapper entry) {
    return true;
  }

  @Override
  public void reset() {
    doReset();
  }

  private void doReset() {
    disposeProfilePanels();
    myAbstractSchemesPanel.reset();
    final InspectionProfileModifiableModel currentModifiableModel = myAbstractSchemesPanel.getModel().getModifiableModelFor(getCurrentProfile());
    myAbstractSchemesPanel.selectScheme(currentModifiableModel);
    showProfile(currentModifiableModel);

    final SingleInspectionProfilePanel panel = getSelectedPanel();
    if (panel != null) {
      panel.setVisible(true);//make sure that UI was initialized
      mySelectionAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
      mySelectionAlarm.cancelAllRequests();
      mySelectionAlarm.addRequest(panel::updateSelection, 200);
    }
  }

  public SingleInspectionProfilePanel createPanel(InspectionProfileModifiableModel profile) {
    return new SingleInspectionProfilePanel(myProjectProfileManager, profile) {
      @Override
      protected boolean accept(InspectionToolWrapper entry) {
        return super.accept(entry) && acceptTool(entry);
      }
    };
  }

  @Override
  public void disposeUIResources() {
    disposeProfilePanels();
    Disposer.dispose(myAbstractSchemesPanel);
  }

  private void disposeProfilePanels() {
    if (mySelectionAlarm != null) {
      Disposer.dispose(mySelectionAlarm);
      mySelectionAlarm = null;
    }
    if (myProfilePanelHolder != null) {
      myProfilePanelHolder.removeAll();
    }
    if (myAbstractSchemesPanel != null) {
      myAbstractSchemesPanel.getModel().disposeUI();
    }
  }

  @Override
  public void selectProfile(InspectionProfileImpl profile) {
    final InspectionProfileModifiableModel modifiableModel = myAbstractSchemesPanel.getModel().getModifiableModelFor(profile);
    showProfile(modifiableModel);
  }

  @Override
  public void selectInspectionTool(String selectedToolShortName) {
    final InspectionProfileModifiableModel inspectionProfile = getSelectedObject();
    final SingleInspectionProfilePanel panel = myAbstractSchemesPanel.getModel().getProfilePanel(inspectionProfile);
    panel.selectInspectionTool(selectedToolShortName);
  }

  @Override
  public void selectInspectionGroup(String[] groupPath) {
    myAbstractSchemesPanel.getModel().getProfilePanel(getSelectedObject()).selectInspectionGroup(groupPath);
  }


  @NotNull
  @Override
  public InspectionProfileModifiableModel getSelectedObject() {
    return myAbstractSchemesPanel.getSelectedScheme();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    final SingleInspectionProfilePanel panel = getSelectedPanel();
    return panel == null ? null : panel.getPreferredFocusedComponent();
  }

  void removeProfilePanel(SingleInspectionProfilePanel profilePanel) {
    myProfilePanelHolder.remove(profilePanel);
  }

  private SingleInspectionProfilePanel getSelectedPanel() {
    final InspectionProfileModifiableModel inspectionProfile = getSelectedObject();
    return myAbstractSchemesPanel.getModel().getProfilePanel(inspectionProfile);
  }

  private void showProfile(InspectionProfileModifiableModel profile) {
    final SingleInspectionProfilePanel panel = myAbstractSchemesPanel.getModel().getProfilePanel(profile);
    if (!ArrayUtil.contains(panel, myAbstractSchemesPanel.getModel().getProfilePanels())) {
      myProfilePanelHolder.add(panel);
    }
    for (Component component : myProfilePanelHolder.getComponents()) {
      component.setVisible(component == panel);
    }
  }
}
