package com.xhs.rewriter.service;

import com.xhs.rewriter.domain.UserAccount;
import com.xhs.rewriter.mapper.UserAccountMapper;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class UserAccountDetailsService implements UserDetailsService {
    private final UserAccountMapper userMapper;

    public UserAccountDetailsService(UserAccountMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccount account = userMapper.findByUsername(username);
        if (account == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new User(account.getUsername(), account.getPassword(), Collections.emptyList());
    }
}
