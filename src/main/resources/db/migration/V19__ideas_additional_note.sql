-- ENG-060: Submit Idea screen redesign separates "Additional Note" (short) from
-- "Idea Description / Details" (notes_remarks, longer) as two distinct fields - the frozen
-- ideas table (ERD-TBL-009) only had one free-text notes column, so this adds a second,
-- purely additive nullable column.
ALTER TABLE ideas ADD COLUMN additional_note TEXT;
