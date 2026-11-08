package bettersounds.client;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Dialog pilih file/folder bawaan sistem operasi (lwjgl-tinyfd, dibundel sebagai jar-in-jar). */
public final class FilePicker {
	private FilePicker() {}

	private static Method find(Class<?> c, String name, int params) {
		for (Method m : c.getMethods()) {
			if (m.getName().equals(name) && m.getParameterCount() == params && m.getParameterTypes()[0] == CharSequence.class) {
				return m;
			}
		}
		return null;
	}

	/** Memilih satu atau banyak file suara. Mengembalikan daftar kosong kalau dibatalkan. */
	public static List<Path> pickFiles() {
		List<Path> out = new ArrayList<>();
		try {
			Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			Method m = find(c, "tinyfd_openFileDialog", 5);
			Object r = m.invoke(null, "Import sounds (.ogg .mp3 .wav)", "", null, null, Boolean.TRUE);
			if (r != null) for (String s : r.toString().split("\\|")) if (!s.isBlank()) out.add(Path.of(s));
		} catch (Throwable t) {
			SoundConfig.LOGGER.warn("Dialog pilih file tidak tersedia", t);
		}
		return out;
	}

	/** Memilih satu folder (menjadi grup suara). */
	public static Path pickFolder() {
		try {
			Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
			Method m = find(c, "tinyfd_selectFolderDialog", 2);
			Object r = m.invoke(null, "Import a folder of sounds", "");
			if (r != null && !r.toString().isBlank()) return Path.of(r.toString());
		} catch (Throwable t) {
			SoundConfig.LOGGER.warn("Dialog pilih folder tidak tersedia", t);
		}
		return null;
	}
}
