package com.localarena;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Registry for the offline game collection. New games can be added without changing the shell. */
public final class GameCatalog {
    public static final class Game {
        public final String id, title, icon;
        public Game(String id,String title,String icon){this.id=id;this.title=title;this.icon=icon;}
    }
    public static final List<Game> ALL = Collections.unmodifiableList(Arrays.asList(
        new Game("CHESS","Шахматы","♟"),
        new Game("SEA","Морской бой","⚓"),
        new Game("DURAK","Дурак","♦")
    ));
    private GameCatalog(){}
}
