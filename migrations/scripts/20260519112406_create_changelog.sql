-- // Create Changelog



-- Be sure that ID and DESCRIPTION fields exist in
-- BigInteger and String compatible fields respectively.

CREATE TABLE ${changelog} (
ID NUMERIC(20,0) NOT NULL,
APPLIED_AT text NOT NULL,
DESCRIPTION text NOT NULL
);

ALTER TABLE ${changelog}
ADD CONSTRAINT PK_${changelog}
PRIMARY KEY (id);

-- //@UNDO

DROP TABLE ${changelog};
