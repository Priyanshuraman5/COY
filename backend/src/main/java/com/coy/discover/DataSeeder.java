package com.coy.discover;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {
    private static final String[] NAMES = {"Aanya","Rohan","Ishita","Kabir","Meera","Arjun","Diya","Vihaan","Saanvi","Aditya",
        "Kiara","Reyansh","Anika","Dev","Tara","Krish","Myra","Ayaan","Riya","Neil",
        "Zoya","Rahul","Pooja","Sameer","Naina","Varun","Sneha","Yash","Avni","Manav"};
    private static final String[] PLACES = {"Galgotias University","Delhi University","IIT Delhi","Christ University","BITS Pilani","Mumbai","Bengaluru","Pune"};
    private static final String[] INTERESTS = {"Music","Cricket","Coding","Travel","Photography","Anime","Cooking","Gym","Movies","Books"};

    private final JdbcTemplate db;

    public DataSeeder(JdbcTemplate db) { this.db = db; }

    @Override
    public void run(String... args) {
        Integer count = db.queryForObject("SELECT COUNT(*) FROM profiles", Integer.class);
        if (count != null && count > 0) return;
        for (int i = 1; i <= NAMES.length; i++) {
            String interests = INTERESTS[i % 10] + "," + INTERESTS[(i + 3) % 10] + "," + INTERESTS[(i + 6) % 10];
            db.update("INSERT INTO profiles VALUES (?,?,?,?,?,?,?)", i, NAMES[i - 1], 19 + i % 7,
                    "https://picsum.photos/seed/coy" + i + "/400/520",
                    "Coffee over small talk. Ask me about " + INTERESTS[i % 10].toLowerCase() + ".",
                    PLACES[i % PLACES.length], interests);
            if (i % 3 == 0) db.update("INSERT INTO incoming_likes VALUES (?)", i); // 10 profiles already liked you
        }
    }
}
