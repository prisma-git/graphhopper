package at.prismasolutions.graphhopper.extension;

import java.io.File;
import java.io.FilenameFilter;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;

public class GHEventChangeListener extends TimerTask {

	private String folderPath = null;
	private Map<String, Long> timeStamps = new HashMap<String, Long>();
	private final static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(GHEventChangeListener.class);
	private final GHEventMapper manager;

	public GHEventChangeListener(final String folderPath, final GHEventMapper manager) {
		this.folderPath = folderPath;
		this.manager = manager;
	}

	@Override
	public void run() {
		this.fileUpdated();

	}

	private void fileUpdated() {
		File f = new File(this.folderPath);
		File[] matchingFiles = f.listFiles(new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith("json");
			}
		});
		boolean changed = false;
		if (matchingFiles != null) {
			for (File file : matchingFiles) {
				long lastTimeStamp = this.timeStamps.getOrDefault(matchingFiles, 0l);
				long currentTimeStamp = file.lastModified();
				if (currentTimeStamp != lastTimeStamp) {
					this.timeStamps.put(file.getAbsolutePath(), currentTimeStamp);
					changed = true;
				}
			}
			if (changed) {
				this.configurationChanged(matchingFiles);
			}
		}

	}

	public void configurationChanged(File[] files) {
		logger.info("Refreshing GHEvents.");
		manager.initilize(files);
	}

}
