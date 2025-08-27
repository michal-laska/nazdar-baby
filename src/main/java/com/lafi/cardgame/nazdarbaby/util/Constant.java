package com.lafi.cardgame.nazdarbaby.util;

import com.vaadin.flow.component.icon.VaadinIcon;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

public final class Constant {

	public static final String POINTS_LABEL = " points";

	public static final String LOGOUT_LABEL = "Logout";
	public static final String OK_LABEL = "OK";

	public static final String COLOR_STYLE = "color";
	public static final String RED_COLOR = "red";

	public static final VaadinIcon PASSWORD_LOCK_ICON = VaadinIcon.KEY;
	public static final VaadinIcon PASSWORD_OPEN_ICON = VaadinIcon.KEY_O;

	public static final Charset CHARSET = StandardCharsets.UTF_8;

	public static final Set<String> BOT_NAMES = Set.of(
            "Zdislava", "Kunhuta", "Uršula", "Bivoj", "Felix", "Osvald", "Pravoslav", "Radovan", "Ctirad", "Doubravka",
            "Otýlie", "Řehoř", "Ida", "Kvido", "Lumír", "Timotej", "Heřman", "Julius", "Ctibor", "Květoslav", "Zikmund",
            "Svatava", "Pankrác", "Servác", "Viola", "Zbyšek", "Valdemar", "Jarmil", "Norbert", "Medard", "Dobroslav",
            "Gita", "Vlkoslav", "Čeněk", "Bořivoj", "Ignác", "Gustav", "Vavřinec", "Bartoloměj", "Augustýn", "Evelína",
            "Vladěna", "Bronislav", "Regina", "Irma", "Jeroným", "Hanuš", "Vendelín", "Jonáš", "Liběna", "Saskie",
            "Bohdan", "Benedikt", "Otmar", "Mahulena", "Cecílie", "Klement", "Xenie", "Ambrož", "Božidara"
    );

	private Constant() {
	}
}
