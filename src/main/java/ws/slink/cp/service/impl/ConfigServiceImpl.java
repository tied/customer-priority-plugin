package ws.slink.cp.service.impl;

import com.atlassian.plugin.spring.scanner.annotation.component.JiraComponent;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import ws.slink.cp.model.StyleElement;
import ws.slink.cp.service.ConfigService;
import ws.slink.cp.tools.Common;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Scanned
@ExportAsService
@JiraComponent
public class ConfigServiceImpl implements ConfigService {

    public static final String CONFIG_PREFIX                  = "ws.slink.customer-priority-plugin";
    public static final String CONFIG_ADMIN_PROJECTS          = "admin.projects";
    public static final String CONFIG_ADMIN_ROLES             = "admin.roles";
    public static final String CONFIG_ADMIN_FIELD_ID          = "admin.field_id";
    public static final String CONFIG_STYLES                  = "config.styles";
    public static final String CONFIG_VIEWERS                 = "config.viewers";

    @ComponentImport
    private final PluginSettingsFactory pluginSettingsFactory;
    private final PluginSettings pluginSettings;

    @Inject
    private ConfigServiceImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
        this.pluginSettings = pluginSettingsFactory.createGlobalSettings();
        System.out.println("----> created config service");
    }

    public Collection<String> getAdminProjects() { // returns list of projectKey
        return getListParam(CONFIG_ADMIN_PROJECTS);
    }
    public Collection<String> getAdminRoles() { // returns list of roleId
        return getListParam(CONFIG_ADMIN_ROLES);
    }
    public void setAdminProjects(String projects) {
         pluginSettings.put(CONFIG_PREFIX + "." + CONFIG_ADMIN_PROJECTS, projects);
    }
    public void setAdminRoles(String roles) {
         pluginSettings.put(CONFIG_PREFIX + "." + CONFIG_ADMIN_ROLES, roles);
    }
    public Optional<String> getAdminParticipantsFieldId() {
        Object value = pluginSettings.get(CONFIG_PREFIX + "." + CONFIG_ADMIN_FIELD_ID);
        if (null != value) {
            return Optional.ofNullable(value.toString());
        }
        return Optional.empty();
    }
    public void setAdminParticipantsFieldId(String value) {
        pluginSettings.put(CONFIG_PREFIX + "." + CONFIG_ADMIN_FIELD_ID, value);
    }

    public List<StyleElement> getStyles(String projectKey) {
        String stylesStr = getConfigValue(projectKey, CONFIG_STYLES);
        if (StringUtils.isBlank(stylesStr)) {
            return Collections.emptyList();
        } else {
            List<StyleElement> result = Common.instance().getGsonObject().fromJson(stylesStr, new TypeToken<ArrayList<StyleElement>>(){}.getType());
            return result.stream().sorted(Comparator.comparing(StyleElement::id)).collect(Collectors.toList());
        }
    }
    public Optional<StyleElement> getStyle(String projectKey, String styleId) {
        return getStyles(projectKey)
                .stream()
                .filter(s -> StringUtils.isNotBlank(s.id()) && s.id().equals(styleId))
                .findFirst()
                ;
    }
    public boolean addStyle(String projectKey, StyleElement style) {
        List<StyleElement> existingStyles = new ArrayList<>(getStyles(projectKey));
        if (StringUtils.isNotBlank(style.id())) {
            if (!existingStyles.isEmpty()) {
                if(existingStyles.stream().filter(s -> StringUtils.isNotBlank(s.id()) && s.id().equals(style.id())).count() > 0) {
                    return false;
                }
            }
        } else {
            style.id(RandomStringUtils.random(10, true, true));
        }
        existingStyles.add(style);
        setStyles(projectKey, existingStyles);
        return true;
    }
    public boolean removeStyle(String projectKey, String styleId) {
        List<StyleElement> styles = getStyles(projectKey);
        Optional<StyleElement> styleToRemove = styles.stream().filter(s -> StringUtils.isNotBlank(s.id()) && s.id().equals(styleId)).findAny();
        if (!styleToRemove.isPresent())
            return false;
        styles.remove(styleToRemove.get());
        setStyles(projectKey, styles);
        return true;
    }
    public void setStyles(String projectKey, List<StyleElement> styles) {
        if (null == styles || styles.isEmpty()) {
            setConfigValue(projectKey, CONFIG_STYLES, null);
        } else {
            String stylesStr = Common.instance().getGsonObject().toJson(styles);
            setConfigValue(projectKey, CONFIG_STYLES, stylesStr);
        }
    }
    public boolean updateStyle(String projectKey, StyleElement style) {
        if (null != style) {
            Optional<StyleElement> found = getStyle(projectKey, style.id());
            if (found.isPresent())
                if (removeStyle(projectKey, style.id()))
                    if (addStyle(projectKey, style))
                        return true;
        }
        return false;
    }

    public boolean addReporter(String projectKey, String styleId, String reporter) {
        AtomicBoolean result = new AtomicBoolean(false);
        getStyle(projectKey, styleId).ifPresent(found -> {
            found.reporters().add(reporter);
            result.set(updateStyle(projectKey, found));
        });
        return result.get();
    }
    public boolean removeReporter(String projectKey, String styleId, String reporter) {
        AtomicBoolean result = new AtomicBoolean(false);
        getStyle(projectKey, styleId).ifPresent(found -> {
            found.reporters().remove(reporter);
            result.set(updateStyle(projectKey, found));
        });
        return result.get();
    }

    public Collection<String> getViewers(String projectKey) {
        try {
            return Arrays.asList(getConfigValue(projectKey, CONFIG_VIEWERS).split(" ")).stream().filter(StringUtils::isNotBlank).collect(Collectors.toList());
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
    public boolean setViewers(String projectKey, Collection<String> value) {
        setConfigValue(projectKey, CONFIG_VIEWERS, value.stream().collect(Collectors.joining(" ")));
        return true;
    }
    public boolean addViewer(String projectKey, String viewer) {
        Collection<String> currentViewers = getViewers(projectKey);
        if (currentViewers.contains(viewer))
            return false;
        currentViewers.add(viewer);
        setViewers(projectKey, currentViewers);
        return true;
    }
    public boolean removeViewer(String projectKey, String viewer) {
        Collection<String> currentViewers = getViewers(projectKey);
        if (!currentViewers.contains(viewer)) {
            return false;
        }
        currentViewers.remove(viewer);
        setViewers(projectKey, currentViewers);
        return true;
    }

    private List<String> getListParam(String param) {
        try {
            Object value = pluginSettings.get(CONFIG_PREFIX + "." + param);
//            System.out.println("----> getListParam: " + (CONFIG_PREFIX + "." + param) + ": " + value);
            if (null == value || StringUtils.isBlank(value.toString())) {
                return Collections.EMPTY_LIST;
            } else {
                return Arrays.stream(value.toString().split(",")).map(s -> s.trim()).collect(Collectors.toList());
            }
        } catch (Exception e) {
            return Collections.EMPTY_LIST;
        }
    }
    private String getParam(String param) {
//        System.out.println("----> getParam: " + param);
        String key = CONFIG_PREFIX + "." + param;
        String value = (String) pluginSettings.get(key);
        if (StringUtils.isBlank(value))
            return "";
        else
            return value;
    }
    private String getConfigValue(String projectKey, String key) {
        try {
            String cfgKey = (StringUtils.isBlank(projectKey))
                    ? CONFIG_PREFIX + "." + key
                    : CONFIG_PREFIX + "." + projectKey + "." + key;
            //        System.out.println("----> get config key " + cfgKey);
            String result = (String) pluginSettings.get(cfgKey);
            return StringUtils.isBlank(result) ? "" : result;
        } catch (Exception e) {
            return "";
        }
    }
    private void setConfigValue(String projectKey, String key, String value) {
        String cfgKey = (StringUtils.isBlank(projectKey))
            ? CONFIG_PREFIX + "." + key
            : CONFIG_PREFIX + "." + projectKey + "." + key;
//        System.out.println("----> set config key " + cfgKey + " to " + value);
        pluginSettings.put(cfgKey, value);
    }
    private String setString(String value, String defaultValue, String newLineReplacement) {
        if (null == value || value.isEmpty())
            return defaultValue;
        else
            return value
                .replaceAll(" +", " ")
                .replaceAll(";" , " ")
                .replaceAll("," , " ")
                .replaceAll("\n", newLineReplacement);
    }
}
