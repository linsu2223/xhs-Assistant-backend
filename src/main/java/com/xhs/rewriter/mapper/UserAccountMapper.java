package com.xhs.rewriter.mapper;

import com.xhs.rewriter.domain.UserAccount;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserAccountMapper {
    @Select("select * from user_account where id = #{id}")
    UserAccount findById(Long id);

    @Select("select * from user_account where username = #{username}")
    UserAccount findByUsername(String username);

    @Select("select count(1) from user_account where username = #{username}")
    int countByUsername(String username);

    @Insert("insert into user_account (username, password, display_name, phone, email, avatar_url, encrypted_cookie, cookie_status, cookie_updated_at, today_usage, month_usage, failed_login_count, locked_until, created_at) " +
            "values (#{username}, #{password}, #{displayName}, #{phone}, #{email}, #{avatarUrl}, #{encryptedCookie}, #{cookieStatus}, #{cookieUpdatedAt}, #{todayUsage}, #{monthUsage}, #{failedLoginCount}, #{lockedUntil}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserAccount userAccount);

    @Update("update user_account set username = #{username}, password = #{password}, display_name = #{displayName}, phone = #{phone}, email = #{email}, avatar_url = #{avatarUrl}, encrypted_cookie = #{encryptedCookie}, cookie_status = #{cookieStatus}, cookie_updated_at = #{cookieUpdatedAt}, today_usage = #{todayUsage}, month_usage = #{monthUsage}, failed_login_count = #{failedLoginCount}, locked_until = #{lockedUntil}, created_at = #{createdAt} where id = #{id}")
    int update(UserAccount userAccount);

    @Select("select count(1) from user_account")
    int countAll();
}
