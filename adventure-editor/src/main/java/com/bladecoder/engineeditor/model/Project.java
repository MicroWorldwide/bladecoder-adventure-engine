/*******************************************************************************
 * Copyright 2014 Rafael Garcia Moreno.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.bladecoder.engineeditor.model;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.lwjgl.opengl.Display;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.SerializationException;
import com.bladecoder.engine.actions.ActionFactory;
import com.bladecoder.engine.assets.EngineAssetManager;
import com.bladecoder.engine.model.BaseActor;
import com.bladecoder.engine.model.Scene;
import com.bladecoder.engine.model.World;
import com.bladecoder.engine.util.Config;
import com.bladecoder.engine.util.EngineLogger;
import com.bladecoder.engineeditor.Ctx;
import com.bladecoder.engineeditor.common.EditorLogger;
import com.bladecoder.engineeditor.common.FolderClassLoader;
import com.bladecoder.engineeditor.common.OrderedProperties;
import com.bladecoder.engineeditor.common.RunProccess;
import com.bladecoder.engineeditor.common.Versions;
import com.bladecoder.engineeditor.setup.BladeEngineSetup;
import com.bladecoder.engineeditor.setup.Dependency;
import com.bladecoder.engineeditor.setup.DependencyBank;
import com.bladecoder.engineeditor.setup.DependencyBank.ProjectDependency;
import com.bladecoder.engineeditor.setup.DependencyBank.ProjectType;
import com.bladecoder.engineeditor.setup.ProjectBuilder;
import com.bladecoder.engineeditor.undo.UndoStack;

public class Project extends PropertyChange {
	public static final String PROP_PROJECTFILE = "projectFile";
	public static final String NOTIFY_SCENE_SELECTED = "SCENE_SELECTED";
	public static final String NOTIFY_ACTOR_SELECTED = "ACTOR_SELECTED";
	public static final String NOTIFY_ANIM_SELECTED = "ANIM_SELECTED";
	public static final String NOTIFY_VERB_SELECTED = "VERB_SELECTED";
	public static final String NOTIFY_PROJECT_LOADED = "PROJECT_LOADED";
	public static final String NOTIFY_PROJECT_SAVED = "PROJECT_SAVED";

	public static final String NOTIFY_ELEMENT_DELETED = "ELEMENT_DELETED";
	public static final String NOTIFY_ELEMENT_CREATED = "ELEMENT_CREATED";
	public static final String NOTIFY_MODEL_MODIFIED = "MODEL_MODIFIED";
	public static final String POSITION_PROPERTY = "pos";
	public static final String WIDTH_PROPERTY = "width";
	public static final String HEIGHT_PROPERTY = "height";
	public static final String CHAPTER_PROPERTY = "chapter";

	public static final String SPINE_RENDERER_STRING = "spine";
	public static final String ATLAS_RENDERER_STRING = "atlas";
	public static final String IMAGE_RENDERER_STRING = "image";
	public static final String S3D_RENDERER_STRING = "3d";

	public static final String ASSETS_PATH = "/android/assets";
	public static final String MODEL_PATH = ASSETS_PATH + "/model";
	public static final String ATLASES_PATH = ASSETS_PATH + "/atlases";
	public static final String FONTS_PATH = ASSETS_PATH + "/fonts";
	public static final String MUSIC_PATH = ASSETS_PATH + "/music";
	public static final String SOUND_PATH = ASSETS_PATH + "/sounds";
	public static final String IMAGE_PATH = ASSETS_PATH + "/images";
	public static final String SPRITE3D_PATH = ASSETS_PATH + "/3d";
	public static final String SPINE_PATH = ASSETS_PATH + "/spine";
	public static final String UI_PATH = ASSETS_PATH + "/ui";

	public static final int DEFAULT_WIDTH = 1920;
	public static final int DEFAULT_HEIGHT = 1080;

	private static final String CONFIG_DIR = System.getProperty("user.home") + "/.AdventureEditor";
	private static final String CONFIG_FILENAME = "config.properties";

	public static final String LAST_PROJECT_PROP = "last_project";

	private final Properties editorConfig = new Properties();

	private File projectFile;

	private final UndoStack undoStack = new UndoStack();
	private Properties projectConfig;

	private I18NHandler i18n;
	private Chapter chapter;
	private Scene selectedScene;
	private BaseActor selectedActor;
	private String selectedFA;
	private boolean modified = false;

	public Project() {
		loadConfig();
	}

	public UndoStack getUndoStack() {
		return undoStack;
	}

	private void loadConfig() {
		File dir = new File(CONFIG_DIR);
		File f = new File(CONFIG_DIR + "/" + CONFIG_FILENAME);

		if (!dir.exists())
			dir.mkdirs();

		try {
			if (!f.exists()) {
				f.createNewFile();
			} else {
				editorConfig.load(new FileInputStream(f));
			}
		} catch (IOException e) {
			EditorLogger.error(e.getMessage());
		}
	}

	public void saveConfig() {
		File f = new File(CONFIG_DIR + "/" + CONFIG_FILENAME);

		try {
			editorConfig.store(new FileOutputStream(f), null);
		} catch (IOException e) {
			EditorLogger.error(e.getMessage());
		}
	}

	public Properties getEditorConfig() {
		return editorConfig;
	}

	public Properties getProjectConfig() {
		return projectConfig;
	}

	public I18NHandler getI18N() {
		return i18n;
	}

	public String translate(String key) {
		return i18n.getTranslation(key);
	}

	public void setModified(Object source, String property, Object oldValue, Object newValue) {
		modified = true;
		PropertyChangeEvent evt = new PropertyChangeEvent(source, property, oldValue, newValue);
		firePropertyChange(evt);
	}

	public void notifyPropertyChange(String property) {
		firePropertyChange(property);
	}

	public void setSelectedScene(Scene scn) {
		selectedScene = scn;
		selectedActor = null;
		selectedFA = null;

		firePropertyChange(NOTIFY_SCENE_SELECTED, null, selectedScene);
	}

	public void setSelectedActor(BaseActor a) {
		BaseActor old = null;

		old = selectedActor;

		selectedActor = a;
		selectedFA = null;

		firePropertyChange(NOTIFY_ACTOR_SELECTED, old, selectedActor);
	}

	public Chapter getChapter() {
		return chapter;
	}

	public Scene getSelectedScene() {
		return selectedScene;
	}

	public BaseActor getSelectedActor() {
		return selectedActor;
	}

	public String getSelectedFA() {
		return selectedFA;
	}

	public void setSelectedFA(String id) {
		String old = selectedFA;

		selectedFA = id;

		firePropertyChange(NOTIFY_ANIM_SELECTED, old, selectedFA);
	}

	public String getModelPath() {
		return projectFile.getAbsolutePath() + MODEL_PATH;
	}

	public String getProjectPath() {
		return projectFile.getAbsolutePath();
	}

	public File getProjectDir() {
		return projectFile;
	}

	public String getTitle() {
		if (projectConfig == null)
			return null;

		return projectConfig.getProperty(Config.TITLE_PROP, getProjectDir().getName());
	}

	public String getPackageTitle() {
		return getTitle().replace(" ", "").replace("'", "");
	}

	public void createProject(String projectDir, String name, String pkg, String sdkLocation, boolean spinePlugin)
			throws IOException {
		createLibGdxProject(projectDir, name, pkg, "BladeEngine", sdkLocation, spinePlugin);

		projectFile = new File(projectDir + "/" + name);

		loadProject(projectFile);
	}

	private void createLibGdxProject(String projectDir, String name, String pkg, String mainClass, String sdkLocation,
			boolean spinePlugin) throws IOException {
		String sdk = null;

		if (sdkLocation != null && !sdkLocation.isEmpty()) {
			sdk = sdkLocation;
		} else if (System.getenv("ANDROID_HOME") != null) {
			sdk = System.getenv("ANDROID_HOME");
		}

		DependencyBank bank = new DependencyBank();
		ProjectBuilder builder = new ProjectBuilder(bank);
		List<ProjectType> projects = new ArrayList<>();
		projects.add(ProjectType.CORE);
		projects.add(ProjectType.DESKTOP);
		projects.add(ProjectType.ANDROID);
		projects.add(ProjectType.IOS);
		// projects.add(ProjectType.HTML);

		List<Dependency> dependencies = new ArrayList<>();
		dependencies.add(bank.getDependency(ProjectDependency.GDX));
		dependencies.add(bank.getDependency(ProjectDependency.FREETYPE));

		if (spinePlugin)
			dependencies.add(bank.getDependency(ProjectDependency.SPINE));

		builder.buildProject(projects, dependencies);
		builder.build();
		new BladeEngineSetup().build(builder, projectDir + "/" + name, name, pkg, mainClass, sdk, null);
	}

	public void saveProject() throws IOException {
		if (projectFile != null && chapter.getId() != null && modified) {

			EngineLogger.setDebug();

			// 1.- SAVE world.json
			World.getInstance().saveWorldDesc(
					new FileHandle(new File(projectFile.getAbsolutePath() + MODEL_PATH + "/world.json")));

			// 2.- SAVE .chapter
			chapter.save();

			// 3.- SAVE BladeEngine.properties
			projectConfig.store(
					new FileOutputStream(
							projectFile.getAbsolutePath() + "/" + ASSETS_PATH + "/" + Config.PROPERTIES_FILENAME),
					null);

			// 4.- SAVE I18N
			i18n.save();

			modified = false;
			firePropertyChange(NOTIFY_PROJECT_SAVED);
		}
	}

	public void closeProject() {
		this.projectFile = null;
	}

	public void loadProject(File projectFile) throws IOException {

		File oldProjectFile = this.projectFile;
		this.projectFile = projectFile;

		if (checkProjectStructure()) {

			// Use FolderClassLoader for loading CUSTOM actions.
			// TODO Add 'core/bin' and '/core/out' folders???

			String classFolder = projectFile.getAbsolutePath() + "/core/build/classes/java/main";

			if (!new File(classFolder).exists()) {
				classFolder = projectFile.getAbsolutePath() + "/core/build/classes/main";
			}

			FolderClassLoader folderClassLoader = new FolderClassLoader(classFolder);
			ActionFactory.setActionClassLoader(folderClassLoader);
			EngineAssetManager.createEditInstance(Ctx.project.getProjectDir().getAbsolutePath() + Project.ASSETS_PATH);

			try {
				World.getInstance().loadWorldDesc();
			} catch (SerializationException ex) {
				// check for not compiled custom actions
				if (ex.getCause() != null && ex.getCause() instanceof ClassNotFoundException) {
					EditorLogger.debug("Custom action class not found. Trying to compile...");
					if (RunProccess.runGradle(Ctx.project.getProjectDir(), "desktop:compileJava")) {
						folderClassLoader.reload();
						World.getInstance().loadWorldDesc();
					} else {
						this.projectFile = null;
						throw new IOException("Failed to run Gradle.");
					}
				} else {
					this.projectFile = null;
					throw ex;
				}
			}

			chapter = new Chapter(Ctx.project.getProjectDir().getAbsolutePath() + Project.MODEL_PATH);
			i18n = new I18NHandler(Ctx.project.getProjectDir().getAbsolutePath() + Project.MODEL_PATH);

			// No need to load the chapter. It's loaded by the chapter combo.
			// loadChapter(World.getInstance().getInitChapter());

			editorConfig.setProperty(LAST_PROJECT_PROP, projectFile.getAbsolutePath());

			projectConfig = new OrderedProperties();
			projectConfig.load(new FileInputStream(
					projectFile.getAbsolutePath() + ASSETS_PATH + "/" + Config.PROPERTIES_FILENAME));
			modified = false;

			Display.setTitle("Adventure Editor v" + Versions.getVersion() + " - " + projectFile.getAbsolutePath());

			firePropertyChange(NOTIFY_PROJECT_LOADED);
		} else {
			this.projectFile = oldProjectFile;
			throw new IOException("Project not found.");
		}
	}

	public boolean checkVersion() throws FileNotFoundException, IOException {
		String editorVersion = getEditorBladeEngineVersion();
		String projectVersion = getProjectBladeEngineVersion();

		if (editorVersion.equals(projectVersion) || editorVersion.endsWith("SNAPSHOT")
				|| editorVersion.indexOf('.') == -1)
			return true;

		if (parseVersion(editorVersion) <= parseVersion(projectVersion))
			return true;

		return false;
	}

	private int parseVersion(String v) {
		int number = 0;
		String[] split = v.split("\\.");

		try {
			for (int i = 0; i < split.length; i++) {
				number += Math.pow(10, (split.length - i) * 2) * Integer.parseInt(split[i]);
			}
		} catch (NumberFormatException e) {
			return 0;
		}

		return number;
	}

	public String getProjectBladeEngineVersion() throws FileNotFoundException, IOException {
		Properties properties = getGradleProperties();

		return properties.getProperty(Config.BLADE_ENGINE_VERSION_PROP, "default");
	}

	public String getEditorBladeEngineVersion() {
		return Versions.getVersion();
	}

	public void updateEngineVersion() throws FileNotFoundException, IOException {
		Properties prop = getGradleProperties();

		prop.setProperty(Config.BLADE_ENGINE_VERSION_PROP, Versions.getVersion());
		prop.setProperty("gdxVersion", Versions.getLibgdxVersion());
		prop.setProperty("roboVMVersion", Versions.getRoboVMVersion());

		prop.setProperty("roboVMGradlePluginVersion", Versions.getROBOVMGradlePluginVersion());
		prop.setProperty("androidGradlePluginVersion", Versions.getAndroidGradlePluginVersion());

		saveGradleProperties(prop);
	}

	public boolean checkProjectStructure() {
		if (!new File(getModelPath()).exists()) {
			projectFile = projectFile.getParentFile();
			if (new File(getModelPath()).exists())
				return true;
			else
				return false;
		}

		return true;
	}

	public BaseActor getActor(String id) {
		return selectedScene.getActor(id, false);
	}

	public List<String> getResolutions() {
		File atlasesPath = new File(projectFile.getAbsolutePath() + ATLASES_PATH);
		ArrayList<String> l = new ArrayList<>();

		File[] list = atlasesPath.listFiles();

		if (list == null)
			return l;

		for (int i = 0; i < list.length; i++) {
			String name = list[i].getName();

			if (list[i].isDirectory()) {
				try {
					Float.parseFloat(name);

					l.add(name);
				} catch (Exception e) {

				}
			}
		}

		return l;
	}

	public String getResDir() {
		return "1";
	}

	public void loadChapter(String selChapter) throws IOException {
		undoStack.clear();

		try {
			chapter.load(selChapter);
		} catch (SerializationException ex) {
			// check for not compiled custom actions
			if (ex.getCause() != null && ex.getCause() instanceof ClassNotFoundException) {
				EditorLogger.debug("Custom action class not found. Trying to compile...");
				if (RunProccess.runGradle(Ctx.project.getProjectDir(), "desktop:compileJava")) {
					FolderClassLoader folderClassLoader = new FolderClassLoader(
							projectFile.getAbsolutePath() + "/core/build/classes/main");
					ActionFactory.setActionClassLoader(folderClassLoader);
					chapter.load(selChapter);
				} else {
					throw new IOException("Failed to run Gradle.");
				}
			} else {
				throw ex;
			}
		}

		i18n.load(selChapter);
	}

	public boolean isModified() {
		return modified;
	}

	public void setModified() {
		modified = true;
		firePropertyChange(NOTIFY_MODEL_MODIFIED);
	}

	public Properties getGradleProperties() throws FileNotFoundException, IOException {
		Properties prop = new Properties();

		prop.load(new FileReader(Ctx.project.getProjectDir().getAbsolutePath() + "/gradle.properties"));

		return prop;
	}

	public void saveGradleProperties(Properties prop) throws IOException {
		FileOutputStream os = new FileOutputStream(
				Ctx.project.getProjectDir().getAbsolutePath() + "/gradle.properties");

		prop.store(os, null);
	}
}
