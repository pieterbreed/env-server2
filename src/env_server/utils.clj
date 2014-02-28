(ns env-server.utils
  )

(defn strings?
  "Returns true if all items in the seq are strings false if not. Calls seq on the argument"
  [s]
  (nil? (some #(not (string? %)) (seq  s))))

