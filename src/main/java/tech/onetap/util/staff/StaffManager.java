package tech.onetap.util.staff;

import com.google.gson.*;
import lombok.Getter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class StaffManager {
    @Getter
    private static final Set<Staff> staffList = new HashSet<>();

    private final File file = new File("onetap/staff.json");

    public static void addStaff(Staff staff) {
        staffList.add(staff);
    }

    public static void removeStaff(String staff) {
        staffList.removeIf(s -> s.name.equalsIgnoreCase(staff));
    }

    public static boolean isStaff(String name) {
        for (Staff staff : staffList) {
            if (staff.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static void clearStaff() {
        staffList.clear();
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (Staff staff : staffList) {
            array.add(staff.name);
        }

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                gson.toJson(array, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        if (!file.exists()) return;

        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonElement element = new JsonParser().parse(reader);
            if (!element.isJsonArray()) return;

            JsonArray array = element.getAsJsonArray();
            staffList.clear();

            for (JsonElement el : array) {
                String name = el.getAsString();
                staffList.add(new Staff(name));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}