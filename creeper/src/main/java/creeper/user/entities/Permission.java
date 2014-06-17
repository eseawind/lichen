package creeper.user.entities;

import creeper.core.entities.UUIDPrimaryKeySupport;

import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * 角色对应的许可
 * @author jcai
 */
@Entity
@Table(name="permissions")
public class Permission extends UUIDPrimaryKeySupport{
    @ManyToOne
    @JoinColumn(name="role_id",nullable = false,updatable = false)
    private Role role;
    private String permission;

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
