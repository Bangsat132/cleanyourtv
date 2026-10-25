package io.github.sumyabazar1.weather;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * The launcher's tile for this add-on, and nothing more: a card has to sit on an activity with
 * {@code MAIN} + {@code LEANBACK_LAUNCHER} that is exported, enabled and the only card in the package
 * (docs/specs/card.md), and the tile's picture is the activity's banner — {@code @drawable/card_banner_placeholder}
 * until WeatherWidget publishes the weather.
 *
 * Launching the card opens the weather page and leaves no empty frame behind: returning from the page lands on Home,
 * where the tile is. It holds no work of its own (no service, no alarm, no network).
 */
public final class CardActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        // A translucent activity: the page replaces it at once, so a remote press never lands between the two.
        startActivity(new Intent(this, WeatherActivity.class));
        finish();
    }
}
