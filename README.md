# clj-pepxml

A Clojure library for parsing pepxml files.

## Usage

Install as:

```clj
[clj-pepxml "0.1.0"
```

Require:

```clj
(:require [clj-pepxml.core :as pep])
```

To get analysis summaries from a pepxml file call `pepxml-parameters`
which returns a hash with a list of analysis summaries as xml
elements. If caled with no second argument all summaries a returned in
the hash, if second argument used only the specified summaries are
returned.

```clj
clj-pepxml.core> (def tf "/path/to/file.pep.xml")
#'clj-pepxml.core/tf
clj-pepxml.core> (pepxml-parameters tf [:peptideprophet_summary])
{:peptideprophet_summary (#clojure.data.xml.Element{:tag :peptideprophet_summary,
:attrs {:version "PeptideProphet  (TPP v4.8.0 PHILAE, Build 201507011425-exported
 (Ubuntu-x86_64))", :author ...
clj-pepxml.core>
```

To get a lazy list of spectrum query hits open the pepxml file as a
buffered reader and call `msms-run-seq` on it. This returns a lazy
list of lazy lists; inner lists represent each msms run in the file.
The first element of each of these lists is an xml element of the
msms_run_summary information (i.e. sample_enzyme, corss_linker,
search_summary and analysis_timestamp) and the rest are each spectrum
query entry, also as xml elements.

```clj
clj-pepxml.core> (with-open [r (io/reader tf)]
                   (let [[info & spects] (-> (msms-run-seq r) first)]
                     (xml1-> (xml-zip info) :sample_enzyme (attr :name))))
"trypsin"
clj-pepxml.core> (with-open [r (io/reader tf)]
                   (let [[info & spects] (-> (msms-run-seq r) first)]
                     (map #(-> (xml1-> (xml-zip %) :search_result
                                       :search_hit node)
                               :attrs)
                          (take 2 spects))))
({:num_missed_cleavages "0", :peptide "LSSAHVYLR", :peptide_prev_aa "K",
:calc_neutral_pep_mass "1044.5717", :num_tol_term "2", :massdiff "-0.021",
:peptide_next_aa "L", :num_matched_ions "8", :hit_rank "1", :tot_num_ions "16",
:protein "36727|m.25974", :protein_descr
"Coiled-coil domain-containing protein 25", :num_tot_proteins "1",
:is_rejected "0"} {:num_missed_cleavages "0", :peptide "LSSAHVYLR",
:peptide_prev_aa "K", :calc_neutral_pep_mass "1044.5717", :num_tol_term "2",
:massdiff "-0.021", :peptide_next_aa "L", :num_matched_ions "7",
:hit_rank "1", :tot_num_ions "16", :protein "36727|m.25974",
:protein_descr "Coiled-coil domain-containing protein 25",
:num_tot_proteins "1", :is_rejected "0"})                
clj-pepxml.core> 
```

All the usual caution should be exercised to not hold onto the head of
a lazy sequence.

Some accessors have been defined and I'll add more as I need them.

## License

Copyright © 2016 Jason Mulvenna

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
