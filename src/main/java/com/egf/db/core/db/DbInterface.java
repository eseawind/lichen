/*		
 * Copyright 2013-5-24 The EGF Co., Ltd. 
 * site: http://www.egfit.com
 * file: $Id: org.eclipse.jdt.ui.prefs,DbInterface.java,fangj Exp$
 * created at:上午10:54:44
 */
package com.egf.db.core.db;

/**
 * 数据库接口
 * @author fangj
 * @version $Revision: 2.0 $
 * @since 1.0
 */
public interface DbInterface {
	
	/**
	 * 获取主键列
	 * @param tableName 表名
	 * @return
	 */
	public String[] getPrimaryKeyColumn(String tableName);
	
	/**
	 * 获取主键名称
	 * @param tableName
	 * @return
	 */
	public String getPrimaryKeyName(String tableName);
	
}