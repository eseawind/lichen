package lichen.creeper.core.models;

import java.util.ArrayList;
import java.util.List;

/**
 * lichen.creeper menu
 * @author jcai
 */
public class CreeperMenu implements Comparable<CreeperMenu>{
    /* 菜单标记定义 */
    public static final int MENU_IS_ROOT = 0x0001;
    public static final int MENU_VISIBLE_IN_TREE = 0x0002;
    public static final int MENU_VISIBLE_IN_BREADCRUMB = 0x0004;
    public static final int MENU_LINKS_TO_PARENT = 0x0008;
    public static final int MENU_MODIFIED_BY_ADMIN = 0x0020;
    public static final int MENU_CREATED_BY_ADMIN = 0x0040;
    public static final int MENU_IS_LOCAL_TASK = 0x0080;
    public static final int MENU_IS_LOCAL_ACTION = 0x0100;
    public static final int MENU_IS_VIRTUAL_ACTION = 0x0200;

    /* 菜单类型 */
    //正常的菜单
    public static final int MENU_NORMAL_ITEM = MENU_VISIBLE_IN_TREE | MENU_VISIBLE_IN_BREADCRUMB;
    //访问之后进行请求
    public static final int MENU_CALLBACK = 0x0000;
    //本地任务菜单,譬如：node/edit/212
    public static final int MENU_LOCAL_TASK = MENU_IS_LOCAL_TASK | MENU_VISIBLE_IN_BREADCRUMB;
    //默认的本地菜单，通常是tab的默认页
    public static final int MENU_DEFAULT_LOCAL_TASK =  MENU_IS_LOCAL_TASK | MENU_LINKS_TO_PARENT | MENU_VISIBLE_IN_BREADCRUMB;
    //本地菜单
    public static final int MENU_LOCAL_ACTION =  MENU_IS_LOCAL_TASK | MENU_IS_LOCAL_ACTION | MENU_VISIBLE_IN_BREADCRUMB;
    //虚拟的菜单，仅仅作为展示使用
    public static final int MENU_VIRTUAL = MENU_IS_VIRTUAL_ACTION;


    //名称
    private String name;
    //标题
    private String title;
    //描述
    private String desc;
    //URL
    private String url;
    //page对应的class
    private Class<?> pageClass;
    //菜单类型
    private int type=MENU_NORMAL_ITEM;
    //菜单排序
    private int order = 0;
    //登录认证,默认false
    private boolean authentication = false;
    //菜单所需权限
    private String[] permissions;
    //父菜单
    private CreeperMenu parent;
    //子菜单
    private List<CreeperMenu> children = new ArrayList<CreeperMenu>() ;
    
	public CreeperMenu(){}
    
    public CreeperMenu(String name,String url){
    	this.name = name;
    	this.url = url;
    }
    
    public CreeperMenu(String name,Class<?> pageClass){
    	this.name = name;
    	this.pageClass = pageClass;
    }
    
    /**
     * 创建CreeperMenu
     * @param name
     * @param url
     * @return
     */
    public static CreeperMenu createCreeperMenu(String name,String url){
    	CreeperMenu menu = new CreeperMenu(name,url);
    	return menu;
    }
    
    /**
     * 创建CreeperMenu
     * @param name
     * @param clazz
     * @return
     */
    public static CreeperMenu createCreeperMenu(String name, Class<?> clazz) {
    	CreeperMenu menu = new CreeperMenu(name,clazz);
    	return menu;
	}
    
    /**
     * 设置标题
     * @param title
     * @return
     */
    public CreeperMenu title(String title){
    	this.title = title;
    	return this;
    }
    
    /**
     * 设置排序
     * @param order
     * @return
     */
    public CreeperMenu order(int order){
    	this.order = order;
    	return this;
    }
    
    /**
     * 设置菜单类型
     * @param type
     * @return
     */
    public CreeperMenu type(int type){
    	this.type = type;
    	return this;
    }
    
    /**
     * 设置是否需登录
     * @param authentication
     * @return
     */
    public CreeperMenu authentication(boolean authentication){
    	this.authentication = authentication;
    	return this;
    }
    
    /**
     * 设置权限
     * @param permissions
     * @return
     */
    public CreeperMenu permissions(String[] permissions){
    	setPermissions(permissions);
    	return this;
    }
    
    public boolean isAuthentication() {
		return authentication;
	}
    
	public void setAuthentication(boolean authentication) {
		this.authentication = authentication;
	}

	public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
    
    public String[] getPermissions() {
		return permissions;
	}
	public void setPermissions(String[] permissions) {
		this.permissions = permissions;
	}
	public CreeperMenu getParent() {
        return parent;
    }

    public void setParent(CreeperMenu parent) {
        this.parent = parent;
    }

    public List<CreeperMenu> getChildren() {
        return children;
    }

    public boolean isType(int type){
        return (this.type | type) == type;
    }
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(" name:").append(name).append(" url:").append(url);
        if(getChildren().size() >0){
            builder.append("\n{");
            for(CreeperMenu menu:getChildren()){
                builder.append(menu);
            }
            builder.append("}\n");
        }
        return builder.toString();
    }

    @Override
    public int compareTo(CreeperMenu o) {
        return this.getOrder() - o.getOrder();
    }

    public CreeperMenu copy() {
        CreeperMenu menuCopied = new CreeperMenu();
        menuCopied.name = name;
        menuCopied.title = title;
        menuCopied.url = url;
        menuCopied.desc = desc;
        menuCopied.type = type;
        menuCopied.order = order;
        menuCopied.parent = parent;
        menuCopied.pageClass = pageClass;
        return menuCopied;
    }

    public Class<?> getPageClass() {
        return pageClass;
    }

    public void setPageClass(Class<?> pageClass) {
        this.pageClass = pageClass;
    }
	
}
