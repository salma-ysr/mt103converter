package com.attijari.MT103converter.repositories;

import com.attijari.MT103converter.models.MT103Msg;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MT103MsgRepository extends MongoRepository<MT103Msg, String> {}
