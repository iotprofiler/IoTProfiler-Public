package dpcollector.manager;

import java.io.FileReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import analysisutils.Globals;
import dpcollector.Utils;

public class AppMetaImporter {
	public Map<String, Meta> metaMap;
	private static AppMetaImporter singleton;

	public class Meta {
		public String appname;
		public String pkgname;
		public String store;
		public String apilevel;
		public String detailurl;
		public String downloadurl;
		public String installs;
		public String updated;
		public String support_website;
		public String description;
		public Set<String> urls_in_description;
	}

	public static AppMetaImporter getInstance() {
		if (singleton == null) {
			singleton = new AppMetaImporter();
		}
		return singleton;
	}

	public void importMetadata() {
		this.metaMap = new HashMap<String, Meta>();

		try {
			FileReader filereader = new FileReader(Globals.CONFIG_DIR + "apk_metadata.csv");
			CSVReader csvReader = new CSVReaderBuilder(filereader).withSkipLines(1).build();
			List<String[]> allData = csvReader.readAll();

			for (String[] row : allData) {
				if (row.length != 12) {
					continue;
				}

				Meta meta = new Meta();
				meta.appname = row[0];
				meta.pkgname = row[1];
				meta.store = row[2];
				meta.apilevel = row[3];
				meta.detailurl = row[4];
				meta.downloadurl = row[6];
				meta.installs = row[7];
				meta.updated = row[8];
				meta.support_website = row[9];

				meta.description = row[11];
				meta.urls_in_description = new HashSet<String>();
				for (String descword : meta.description.split(" ")) {
					if (!descword.contains(".")) {
						continue;
					}

					if (Utils.isURL(descword) && !descword.contains("play.google.com")) {
						meta.urls_in_description.add(descword);
					}
				}

				this.metaMap.put(meta.pkgname, meta);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isURL3P(String url, String pkg) {
		// compare url to app package name
		List<String> pkgDotList = Arrays.asList(pkg.split("\\."));
		Collections.reverse(pkgDotList);
		String reversePkg = String.join(".", pkgDotList);
		
		// naive check of SLD
		if (pkgDotList.size() >= 2 && url.contains(pkgDotList.get(pkgDotList.size() - 2) + ".")) {
			return false;
		}
		
		if (!Utils.hasDiffDomain(reversePkg, url)) {
			return false;
		}
		
		// compare url to app metadata
		if (this.metaMap.containsKey(pkg)) {
			Meta meta = this.metaMap.get(pkg);
	
			// compare url to supporting website of the app
			String support_website = meta.support_website;
			if (Utils.isURL(support_website)) {
				boolean isDiffDomain = Utils.hasDiffDomain(support_website, url);
				if (!isDiffDomain) {
					return false;
				}
			}
	
			// compare the url to any website mentioned in app description
			for (String durl : meta.urls_in_description) {
				boolean isDiffDomain = Utils.hasDiffDomain(durl, url);
				if (!isDiffDomain) {
					return false;
				}
			}
		}

		return true;
	}
}