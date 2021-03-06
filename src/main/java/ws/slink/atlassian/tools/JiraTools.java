package ws.slink.atlassian.tools;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.roles.ProjectRole;
import com.atlassian.jira.security.roles.ProjectRoleManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.sal.api.user.UserProfile;
import ws.slink.atlassian.service.ConfigService;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class JiraTools {

    public static boolean isPluginManager(UserProfile user) {
        return isPluginManager(user.getUsername());
    }
    public static boolean isPluginManager(ApplicationUser user) {
        return isPluginManager(user.getUsername());
    }
    private static boolean isPluginManager(String user) {
        Project currentProject = ComponentAccessor.getProjectManager().getProjectObj(new ProjectActionSupport().getSelectedProjectId());

        boolean canManage = (null != currentProject
            && ConfigService.instance().projectsList().contains(currentProject.getKey())
            && userHasRoles(
                currentProject,
                ConfigService.instance().rolesList().stream()
                    .map(JiraTools::getProjectRoleByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ComponentAccessor.getUserManager().getUserByName(user))
        );
//        System.out.println(user + ((canManage) ? " can " : " can't ") + "manage " + currentProject.getKey());
        return canManage;


    }

    public static boolean isViewer(String projectKey, ApplicationUser applicationUser) {
        boolean result =
            // TODO: add support for "*" to allow views for all ?
            Arrays.asList(ConfigService.instance().getViewers(projectKey).trim().split(" "))
                .stream()
                .anyMatch(s -> ((s.contains("*")
                    && applicationUser.getEmailAddress().toLowerCase().contains(s.replaceAll("\\*", "").toLowerCase()))
                    || applicationUser.getEmailAddress().equalsIgnoreCase(s))
                );
        return result;
    }

    public static boolean userHasRolesInProjects(Collection<Project> projects, Collection<ProjectRole> roles, ApplicationUser user) {
        return projects.stream().filter(Objects::nonNull).anyMatch(p -> roles.stream().filter(Objects::nonNull).anyMatch(r -> userHasRoleInProject(p, user, r)));
    }
    public static boolean userHasRoles(Project project, List<ProjectRole> roles, ApplicationUser user) {
        return userHasRolesInProjects(Arrays.asList(project), roles, user);
    }
    public static boolean userHasRoleInProject(Project project, ApplicationUser user, ProjectRole role) {
        ProjectRoleManager projectRoleManager = ComponentAccessor.getComponentOfType(ProjectRoleManager.class);
        boolean result = projectRoleManager.isUserInProjectRole(user, role, project);
        // System.out.println(user.getUsername() + " : " + project.getKey() + " : " + role.getName() + " => " + result);
        return result;
    }
    public static ProjectRole getProjectRoleByKey(String roleKey) {
        try {
            ProjectRole projectRole = ComponentAccessor.getComponent(ProjectRoleManager.class).getProjectRole(roleKey);
            return projectRole;
        } catch (Exception e) {
            // System.err.println("ERROR CONVERTING ROLE KEY TO PROJECT ROLE: " + e.getMessage());
            return null;
        }
    }
    public static Project getProjectByKey(String projectKey) {
        try {
            return ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
        } catch (Exception e) {
            // System.err.println("ERROR CONVERTING PROJECT KEY TO PROJECT: " + e.getMessage());
            return null;
        }
    }
    public static ApplicationUser getLoggedInUser() {
        JiraAuthenticationContext jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        return jiraAuthenticationContext.getLoggedInUser();
    }
}


/*
        if (StringUtils.isBlank(ConfigService.instance().getRoles().trim())
         && StringUtils.isBlank(ConfigService.instance().getProjects().trim())) {
            System.out.println("~~~~ BRANCH 1");
            return true;
        }

        if (null != currentProject
         && StringUtils.isBlank(ConfigService.instance().getRoles().trim())
         && ConfigService.instance().getProjects().contains(currentProject.getKey())) {
            System.out.println("~~~~ BRANCH 2");
            return true;
        }

        if ( null != currentProject
          && StringUtils.isBlank(ConfigService.instance().getProjects().trim())) {
            System.out.println("~~~~ BRANCH 3");
            return userHasRoles(
                currentProject,
                ConfigService.instance().rolesList().stream()
                        .map(JiraTools::getProjectRoleByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ComponentAccessor.getUserManager().getUserByName(user))
            ;
        }

//        return false;

        System.out.println("~~~~ BRANCH 4");
        return (userHasRolesInProjects(
                ConfigService.instance().projectsList().stream()
                        .map(JiraTools::getProjectByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ConfigService.instance().rolesList().stream()
                        .map(JiraTools::getProjectRoleByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ComponentAccessor.getUserManager().getUserByName(user)));

        /*
        return

          // plugin admin part not configured, allow for all
            ( StringUtils.isBlank(ConfigService.instance().getRoles().trim())
          &&  StringUtils.isBlank(ConfigService.instance().getProjects().trim()))

          // only projects set in admin-config, allow for everyone in these projects
          || ( null != currentProject
          &&   StringUtils.isBlank(ConfigService.instance().getRoles().trim())
          &&   ConfigService.instance().getProjects().contains(currentProject.getKey()))

          // only roles set in admin-config, allow these roles for current project
          || ( null != currentProject
          &&   StringUtils.isBlank(ConfigService.instance().getProjects().trim())
          &&   userHasRoles(
                    currentProject,
                    ConfigService.instance().rolesList().stream()
                        .map(JiraTools::getProjectRoleByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                    ComponentAccessor.getUserManager().getUserByName(user)))

          // only roles set in admin-config, allow these roles for current project
          ||  userHasRolesInProjects(
                ConfigService.instance().projectsList().stream()
                    .map(JiraTools::getProjectByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ConfigService.instance().rolesList().stream()
                        .map(JiraTools::getProjectRoleByKey).filter(Objects::nonNull).collect(Collectors.toList()),
                ComponentAccessor.getUserManager().getUserByName(user))
        ;
        */