package lichen.creeper.user.internal;

import java.util.ArrayList;
import java.util.Collection;

import javax.inject.Inject;

import lichen.creeper.user.dao.RoleDao;
import lichen.creeper.user.dao.UserDao;
import lichen.creeper.user.entities.QRole;
import lichen.creeper.user.entities.QUser;
import lichen.creeper.user.entities.Role;
import lichen.creeper.user.entities.User;
import lichen.creeper.user.services.UserSavedListener;
import lichen.creeper.user.services.UserService;

import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.PasswordService;
import org.apache.shiro.subject.Subject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.mysema.query.types.ExpressionUtils;

/**
 * 用户注册的实现类
 * @author jcai
 */
public class UserServiceImpl implements UserService{
	
    
    @Inject
    private UserDao _userDao;
    
    @Inject
    private RoleDao _roleDao;
    
    @Inject
    private PasswordService _passwordService;
    @Inject
    private Subject _subject;
    @Inject
    private UserSavedListener userSavedListener;
    @Override
    public void register(User user) {
        //加密密码
        String passwordEncrypted = _passwordService.encryptPassword(user.getPass());
        user.setPass(passwordEncrypted);
        _userDao.save(user);
        userSavedListener.afterSaved(user);
    }

    @Override
    public void login(String name, String password) {
        UsernamePasswordToken token = new UsernamePasswordToken(name, password);
        token.setRememberMe(true);
        //TODO catch exception to show message
//        try{
        	_subject.login(token);
//        }catch(UnknownAccountException e){
//        	//user do not exist
//        }catch(IncorrectCredentialsException e){
//        	//invalid password
//        }catch( AuthenticationException e){
//        	throw CreeperException.wrap(e);
//        }
        //TODO 保存用户相关信息
    }

    @Override
    public void logout() {
        //TODO 保存用户相关信息
        _subject.logout();
    }

	@Override
	public void saveOrUpdate(Role role) {
		_roleDao.save(role);
	}
	
	@Override
	public Iterable<Role> findAllRole(final Role role) {
		if(null != role){
			QRole r = QRole.role;
			Collection<com.mysema.query.types.Predicate> list = new ArrayList<com.mysema.query.types.Predicate>();
			if(null != role.getId()){list.add(r.id.eq(role.getId()));}
			if(null != role.getName()){list.add(r.name.eq(role.getName()));}
			return _roleDao.findAll(ExpressionUtils.allOf(list));
		}
		return null;
	}
	
	@Override
	public Page<User> findAll(User user,Pageable pageable){
		if(null != user){
			QUser u = QUser.user;
			Collection<com.mysema.query.types.Predicate> list = new ArrayList<com.mysema.query.types.Predicate>();
			if(null != user.getName()){list.add(u.name.eq(user.getName()));}
			if(null != user.getMail()){list.add(u.mail.eq(user.getMail()));}
			
			Page<User> result = _userDao.findAll(ExpressionUtils.allOf(list), pageable);
			return result;
		}
		return null;
	}
	
	@Override
	public void saveOrUpdate(User user) {
		_userDao.save(user);
        userSavedListener.afterSaved(user);
	}

	@Override
	public Page<Role> findAll(Role role, Pageable pageable) {
		if(null != role){
			QRole r = QRole.role;
			Collection<com.mysema.query.types.Predicate> list = new ArrayList<com.mysema.query.types.Predicate>();
			if(null != role.getId()){list.add(r.id.eq(role.getId()));}
			if(null != role.getName()){list.add(r.name.eq(role.getName()));}
			return _roleDao.findAll(ExpressionUtils.allOf(list),pageable);
		}
		return null;
	}
}