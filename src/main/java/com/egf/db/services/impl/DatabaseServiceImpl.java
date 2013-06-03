/*		
 * Copyright 2013-4-28 The EGF Co., Ltd. 
 * site: http://www.egfit.com
 * file: $Id: org.eclipse.jdt.ui.prefs,DatabaseServiceImpl.java,fangj Exp$
 * created at:下午01:08:24
 */
package com.egf.db.services.impl;

import org.apache.log4j.Logger;

import com.egf.db.core.CreateTableCallback;
import com.egf.db.core.db.DbFactory;
import com.egf.db.core.db.DbInterface;
import com.egf.db.core.define.ColumnType;
import com.egf.db.core.define.IndexType;
import com.egf.db.core.define.column.ColumnDefine;
import com.egf.db.core.define.column.Comment;
import com.egf.db.core.define.column.Default;
import com.egf.db.core.define.column.NotNull;
import com.egf.db.core.define.column.PrimaryKey;
import com.egf.db.core.define.column.Unique;
import com.egf.db.core.define.name.ColumnName;
import com.egf.db.core.define.name.ForeignKeyName;
import com.egf.db.core.define.name.IndexName;
import com.egf.db.core.define.name.PrimaryKeyName;
import com.egf.db.core.define.name.TableName;
import com.egf.db.core.define.name.UniqueName;
import com.egf.db.core.jdbc.JdbcService;
import com.egf.db.core.jdbc.JdbcServiceImpl;
import com.egf.db.core.sql.template.Generate;
import com.egf.db.exception.MigrationException;
import com.egf.db.services.DatabaseService;
import com.egf.db.utils.StringUtils;

/**
 * @author fangj
 * @version $Revision: 2.0 $
 * @since 1.0
 */
class DatabaseServiceImpl implements DatabaseService{

	Logger logger=Logger.getLogger(DatabaseServiceImpl.class);
	
	private final String PRIMARY_KEY="primary key";
	
	private final String FOREIGN_KEY="foreign key";
	
	private final String UNIQUE_KEY="unique";
	
	private final String NOT_NULL="not null";
	
	private final static String HANDLE_COLUMN_TYPE_ADD="add";
	
	private final static String HANDLE_COLUMN_TYPE_CHANGE="change";
	
	private JdbcService jdbcService=new JdbcServiceImpl();
	
	private Generate generate=new GenerateImpl();
	
	//仅仅用来测试
	void setJdbcService(JdbcService jdbcService){
		this.jdbcService = jdbcService;
	}
	
	public void createTable(TableName tableName, Comment comment,CreateTableCallback createTableCallback)throws MigrationException {
		TableImpl tmi=new TableImpl();
		String tableComment="";
		//进行回调操作
		createTableCallback.doCreateAction(tmi);
		StringBuffer columns=tmi.columns;
		columns=columns.delete(columns.length()-2, columns.length());
		String tn=tableName.getName();
		if(tn.indexOf(".")!=-1){
			//创建用户
			DbInterface di=DbFactory.getDb();
			String schema=tn.split("\\.")[0];
			di.createSchema(schema);
		}
		String sql=String.format("create table %s (\n%s\n);",tableName.getName(),columns.toString());
		if(comment!=null){
			tableComment=String.format("comment on table %s is '%s';\n",tableName.getName(),comment.getComment());
		}
		String commentsSql=tmi.comments.toString();
		commentsSql=commentsSql.replaceAll("TN", tableName.getName());
		logger.info("\n"+sql+"\n"+tableComment+commentsSql.toString());
		jdbcService.execute(sql+"\n"+tableComment+commentsSql.toString());
	}
	
	public void createTable(TableName tableName, CreateTableCallback createTableCallback) throws MigrationException{
		createTable(tableName, null, createTableCallback);
	}
	
	public void addColumn(TableName tableName, ColumnName columnName,ColumnType columnType, ColumnDefine... define) throws MigrationException{
		this.handleColumn(HANDLE_COLUMN_TYPE_ADD, tableName, columnName, columnType, define);
	}
	
	public void changeColumn(TableName tableName, ColumnName columnName,ColumnDefine... define) throws MigrationException {
		changeColumn(tableName, columnName,null, define);
	}
	
	public void changeColumn(TableName tableName, ColumnName columnName,ColumnType columnType, ColumnDefine... define)throws MigrationException {
		this.handleColumn(HANDLE_COLUMN_TYPE_CHANGE, tableName, columnName, columnType, define);
	}
	
	public void addIndex(TableName tableName, ColumnName... columnName)throws MigrationException {
		addIndex(tableName, null, null, columnName);
	}

	
	public void addIndex(TableName tableName, IndexType indexType,ColumnName... columnName) throws MigrationException {
		addIndex(tableName, null, indexType, columnName);
	}
	
	public void addIndex(TableName tableName, IndexName IndexName,ColumnName... columnName) throws MigrationException{
		addIndex(tableName, IndexName, null, columnName);
	}

	public void addIndex(TableName tableName, IndexName indexName, IndexType indexType,ColumnName... columnName) throws MigrationException{
		String[] columnNames=new String[columnName.length];
		String tn=tableName.getName();
		String in=null;
		String sql=null;
		if(indexName==null){
		}else{
			 in=indexName.getName();
		}
		for (int i=0;i<columnName.length;i++) {
			ColumnNameImpl columnImpl=(ColumnNameImpl)columnName[i];
			columnNames[i]=columnImpl.getName();
		}
		if(indexType!=null){
			String type=indexType.getIndexType();
			sql=generate.addIndex(tn,in,type,columnNames);
		}else {
			sql=generate.addIndex(tn, in, columnNames);	
		}
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void addPrimaryKey(PrimaryKeyName primaryKeyName, TableName tableName,ColumnName... columnName) throws MigrationException{
		StringBuffer sb=new StringBuffer();
		//修正主键不能为空
		for (ColumnName cn : columnName) {
			String columnType=jdbcService.getColumnTypeName(tableName.getName(), cn.getName());
			String sql=  generate.changeColumn(tableName.getName(), cn.getName(), columnType,null,null,null);
			sb.append(sql+"\n");
		}
		String sql=addKey(primaryKeyName.getName(), tableName,null, PRIMARY_KEY, columnName);
		sb.append(sql+"\n");
		logger.info("\n"+sb.toString());
		jdbcService.execute(sb.toString());
	}
	
	public void addForeignKey(ForeignKeyName foreignKeyName, TableName tableName,TableName refTableName,ColumnName... columnName) throws MigrationException{
		String sql=addKey(foreignKeyName.getName(), tableName,refTableName,FOREIGN_KEY, columnName);
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void addUnique(UniqueName uniqueName, TableName tableName, ColumnName... columnName) throws MigrationException{
		String sql=addKey(uniqueName.getName(), tableName,null,UNIQUE_KEY, columnName);
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void dropTable(TableName tableName) throws MigrationException{
		String sql=generate.dropTalbe(tableName.getName());
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}
	
	public void dropColumn(TableName tableName,ColumnName columnName) throws MigrationException{
		String tn=tableName.getName();
		String cn=columnName.getName();
		String sql=generate.dropColumn(tn, cn);
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void dropIndex(IndexName indexName) throws MigrationException{
	    String sql=	generate.dropIndex(indexName.getName());
	    logger.info("\n"+sql);
	    jdbcService.execute(sql);
	}
	
	public void dropForeignKey(TableName tableName,ForeignKeyName foreignKeyName) throws MigrationException{
		String sql=dropKey(tableName, foreignKeyName.getName());
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void dropPrimaryKey(TableName tableName) throws MigrationException{
		//查询表对应的主键名称
		DbInterface di=DbFactory.getDb();
		String pkName=di.getPrimaryKeyName(tableName.getName());
		String sql=dropKey(tableName, pkName);
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}

	public void dropUnique(TableName tableName,UniqueName uniqueName) throws MigrationException{
		String sql=dropKey(tableName, uniqueName.getName());
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}
	
	public void addComment(TableName tableName, ColumnName columnName,Comment comment) throws MigrationException{
		String tn=tableName.getName();
		String cn=columnName.getName();
		String c=comment.getComment();
		String sql=generate.addComment(tn, cn, c);
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}
	
	
	public void changeComment(TableName tableName, ColumnName columnName,Comment comment) throws MigrationException{
		addComment(tableName, columnName, comment);
	}

	private void handleColumn(String handleType,TableName tableName, ColumnName columnName,ColumnType columnType, ColumnDefine... define){
		Default deft=null;
		NotNull notNull=null;
		Comment comment=null;
		Unique unique=null;
		String sql=null;
		PrimaryKey primaryKey=null;
		for (ColumnDefine columnDefine : define) {
			if(columnDefine!=null){
				if(columnDefine instanceof Default){
					deft=(Default)columnDefine;
				}else if(columnDefine instanceof NotNull){
					notNull=(NotNull)columnDefine;
				}else if (columnDefine instanceof Comment){
					comment=(Comment)columnDefine;
				}else if(columnDefine instanceof Unique){
					unique=(Unique)columnDefine;
				}else if(columnDefine instanceof PrimaryKey){
					primaryKey=(PrimaryKey)columnDefine;
				}
			}
		}
		if(HANDLE_COLUMN_TYPE_ADD.equals(handleType)){
			sql=generate.addColumn(tableName.getName(), columnName.getName(), columnType.getColumnType(),notNull==null?null:NOT_NULL, deft==null?null:deft.getValue(), comment==null?null:comment.getComment(), unique==null?null:UNIQUE_KEY, primaryKey==null?null:PRIMARY_KEY);
		}else if(HANDLE_COLUMN_TYPE_CHANGE.equals(handleType)){
			sql=generate.changeColumn(tableName.getName(), columnName.getName(),columnType==null?null:columnType.getColumnType(),notNull==null?null:NOT_NULL, deft==null?null:deft.getValue(), comment==null?null:comment.getComment());
		}
		logger.info("\n"+sql);
		jdbcService.execute(sql);
	}
	
	private String addKey(String name, TableName tableName,TableName refTableName, String keyType,ColumnName... columnName){
		String[] columnNames=new String[columnName.length];
		String sql=null;
		String tn=tableName.getName();
		for (int i=0;i<columnName.length;i++) {
			ColumnNameImpl columnImpl=(ColumnNameImpl)columnName[i];
			columnNames[i]=columnImpl.getName();
		}
		if(refTableName!=null){
			//获取主键对应的列
			DbInterface di=DbFactory.getDb();
			String[] referencesColumns=di.getPrimaryKeyColumn(refTableName.getName());
			String referencesColumn=StringUtils.getUnionStringArray(referencesColumns, ",");
			sql=generate.addForeignKey(tn, name, refTableName.getName(), referencesColumn, columnNames);
		}else{
			sql=generate.addConstraint(tn, name, keyType, columnNames);
		}
		return sql;
	}
	
	private String dropKey(TableName tableName,String name){
		String tn=tableName.getName();
	    String sql=generate.dropConstraint(tn, name);
	    return sql;
	}
	
}
