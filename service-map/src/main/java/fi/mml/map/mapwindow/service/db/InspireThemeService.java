package fi.mml.map.mapwindow.service.db;

import fi.nls.oskari.domain.map.InspireTheme;
import fi.nls.oskari.service.db.BaseService;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Interface for InspireTheme service
 * 
 *
 */
public interface InspireThemeService extends BaseService<InspireTheme> {
    public List<InspireTheme> findByMaplayerId(final int layerId);
    public void updateLayerThemes(final long maplayerId, final Collection<InspireTheme> themes);
}
